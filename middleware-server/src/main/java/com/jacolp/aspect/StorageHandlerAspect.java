package com.jacolp.aspect;

import com.jacolp.annotation.StorageHandler;
import com.jacolp.constant.ImageConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.NoteMapper;
import com.jacolp.mapper.RoleMapper;
import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.dto.user.UserQuoteStorageDTO;
import com.jacolp.result.Result;
import com.jacolp.enums.StorageOperationType;
import com.jacolp.pojo.entity.NoteEntity;
import com.jacolp.component.LockOperator;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.jacolp.context.BaseContext;
import com.jacolp.context.StorageUpdateContext;
import com.jacolp.mapper.ImageMapper;
import com.jacolp.pojo.entity.UserEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存储空间校验切面 - 配额校验 + 一致性保证 + 存储空间更新
 * <p>所有存储变更（上传/修改/删除/批量删除）统一经过此切面，保证缓存与实际一致。</p>
 * <p>Order=2，在 ImageLimitAspect 后执行。</p>
 */
@Aspect
@Component
@Order(2)
@Slf4j
public class StorageHandlerAspect {

    @Autowired private LockOperator lockOperator;

    @Autowired private ImageMapper imageMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private NoteMapper noteMapper;
    @Autowired private RoleMapper roleMapper;

    @Pointcut("@annotation(storageHandler)")
    public void storageHandlerPointcut(StorageHandler storageHandler) {
    }

    /**
     * 存储空间校验与更新入口。
     * <p>根据操作类型路由到不同的处理策略：</p>
     * <p>- DELETE / BATCH_DELETE：先执行业务，再加锁更新存储</p>
     * <p>- UPLOAD / MODIFY：先一致性检查，再配额校验，执行业务后直接写回（不加二次查询）</p>
     */
    @Transactional(rollbackFor = Exception.class)
    @Around("storageHandlerPointcut(storageHandler)")
    public Object storageHandler(ProceedingJoinPoint joinPoint,
                                 StorageHandler storageHandler) throws Throwable {
        StorageOperationType opType = storageHandler.operationType();
        // 处理多个文件删除操作
        if (opType == StorageOperationType.BATCH_DELETE) {
            return handleBatchDelete(joinPoint);
        }
        // 处理单个文件删除操作
        if (opType == StorageOperationType.DELETE) {
            return handleSingleDelete(joinPoint);
        }

        // 处理单个文件上传/修改
        return handleSingleFileOperation(joinPoint, storageHandler);
    }

    // ======================== 单次删除策略 ========================

    /**
     * 处理用户单次删除。
     * <p>流程：加用户锁 → 执行业务 → 从 ThreadLocal 取文件大小 → 查用户表取缓存值 → 直接运算写回</p>
     * <p>删除只会减少空间，无需一致性检查，直接用缓存值减文件大小。</p>
     */
    private Object handleSingleDelete(ProceedingJoinPoint joinPoint) throws Throwable {
        Long userId = BaseContext.getCurrentId();
        String lockKey = getLockKey(userId);
        String owner = getLockOwner();

        boolean acquired = false;
        try {
            // 加用户锁，最大等待 300ms
            acquired = lockOperator.tryLock(lockKey, owner, 300);
            if (!acquired) {
                throw new BaseException("系统繁忙，请稍后！");
            }

            // 执行业务
            Object result = joinPoint.proceed();

            // 获取被删除文件大小
            syncStorageAfterDelete(userId);
            return result;
        } finally {
            if (acquired) {
                lockOperator.releaseLock(lockKey, owner);
            }
            StorageUpdateContext.clear();   // 清理 ThreadLocal
        }
    }

    /**
     * 删除后同步存储空间。
     * <p>从 ThreadLocal 取被删文件大小，查用户表取缓存值，直接运算后写回。</p>
     * @throws BaseException 如果 ThreadLocal 中不存在对应的上下文 会抛出异常
     */
    private void syncStorageAfterDelete(Long userId) {
        // 获取被删除文件大小（从 ThreadLocal 中获取）
        Map<Long, Long> userStorageMap = StorageUpdateContext.getStorageMap();
        Long fileSize = userStorageMap != null ? userStorageMap.get(userId) : null;
        if (fileSize == null) {
            log.error("Failed to get file size from ThreadLocal");
            throw new BaseException("未知错误");
        }

        // 查询用户已使用的配额
        UserQuoteStorageDTO quote = userMapper.selectQuoteStorageById(userId);
        long currentUsed = quote.getUsedStorageBytes() != null ? quote.getUsedStorageBytes() : 0L;
        long newUsed = Math.max(0L, currentUsed - fileSize);    // 保底最小为 0

        // 更新到DB
        UserEntity updateUser = new UserEntity();
        updateUser.setId(userId);
        updateUser.setUsedStorageBytes(newUsed);
        int count = userMapper.updateById(updateUser);
        if (count <= 0) {
            log.error("Failed to update user storage after delete, userId: {}", userId);
            throw new BaseException(UserConstant.UPDATE_USER_STORAGE_FAILED);
        }
    }

    // ======================== 批量删除策略 ========================

    /**
     * 处理管理员批量删除。
     * <p>deleteImages() 通过 ThreadLocal 传递用户存储变更 Map，</p>
     * <p>本方法在业务执行后获取该 Map，批量加锁并更新存储。</p>
     */
    private Object handleBatchDelete(ProceedingJoinPoint joinPoint) throws Throwable {

        Object result;
        List<Long> acquiredUserIds = null;
        String owner = getLockOwner();

        try {
            result = joinPoint.proceed();
            // 尝试从上下文中获取 文件大小 Map
            Map<Long, Long> userStorageMap = StorageUpdateContext.getStorageMap();
            if (userStorageMap == null || userStorageMap.isEmpty()) {
                log.error("Failed to get file size from ThreadLocal");
                throw new BaseException("未知错误");
            }

            // 尝试获取所有用户的锁（最多等待 1000 ms）
            acquiredUserIds = acquireBatchLocks(userStorageMap, owner, 1000);
            // 锁全部获取失败
            if (acquiredUserIds == null || acquiredUserIds.isEmpty()) {
                throw new BaseException("系统繁忙，请稍后重试");
            }

            // 判断一下有没有全部拿到（防止出现没拿全的情况，但是又拿了锁，需要释放）
            if (acquiredUserIds.size() != userStorageMap.size()) {
                throw new BaseException("系统繁忙，请稍后重试");
            }

            // 批量更新用户存储
            batchUpdateUserStorage(userStorageMap);
        } finally {
            if (acquiredUserIds != null && !acquiredUserIds.isEmpty()) {
                releaseBatchLocks(acquiredUserIds, owner); // 释放所有已获取的用户锁
            }
            StorageUpdateContext.clear();   // 清理 ThreadLocal
        }

        return result;
    }

    // ======================== 单文件操作策略（上传/修改） ========================

    /**
     * 处理单文件操作（上传/修改）的配额校验与存储更新。
     * <p>流程：查用户表取 maxBytes → 查 note+image 两表算 actualUsedBytes →</p>
     * <p>配额校验 → 执行业务 → 基于 actualUsedBytes 直接运算写回（不再重新 sum）</p>
     * <p>全程以 actualUsedBytes（实际值）为基准，配额判断准确。</p>
     */
    private Object handleSingleFileOperation(ProceedingJoinPoint joinPoint,
                                              StorageHandler annotation) throws Throwable {
        MultipartFile file = extractMultipartFile(joinPoint);
        if (file == null || file.isEmpty()) {
            return joinPoint.proceed();
        }

        Long userId = BaseContext.getCurrentId();
        long requiredBytes = resolveRequiredBytes(joinPoint, annotation, file);

        String lockKey = getLockKey(userId);
        String owner = getLockOwner();
        boolean acquired = false;
        try {
            // 0. 加用户锁，最大等待 300ms
            acquired = lockOperator.tryLock(lockKey, owner, 300);
            if (!acquired) {
                throw new BaseException("系统繁忙，请稍后！");
            }
            // 1. 查用户表获取最大配额
            CachedStorageInfo cachedInfo = loadCachedStorageInfo(userId);

            // 2. 查 note + image 两表算实际使用量（一致性检查）
            long actualUsedBytes = computeActualUsedStorage(userId);

            // 3. 配额校验（基于实际值）
            if (actualUsedBytes + requiredBytes > cachedInfo.maxBytes()) {
                throw new BaseException(ImageConstant.IMAGE_STORAGE_QUOTA_EXCEEDED);
            }

            // 4. 执行业务逻辑
            Object result = joinPoint.proceed();

            // 5. 操作后写回（基于 actualUsedBytes 直接运算，不再用缓存值，不再重新 sum）
            syncStorageAfterUploadOrModify(userId, result, actualUsedBytes,
                                           cachedInfo.maxBytes(), annotation.operationType(), requiredBytes);

            return result;
        } finally {
            if (acquired) {
                lockOperator.releaseLock(lockKey, owner);
            }
            StorageUpdateContext.clear();   // 清理 ThreadLocal
        }
    }

    /**
     * 从用户表加载缓存的存储信息（仅用于获取 maxBytes）。
     * @return 返回该用户的最大存储额度
     */
    private CachedStorageInfo loadCachedStorageInfo(Long userId) {
        // 尝试从用户表中获取存储信息
        UserQuoteStorageDTO quote = userMapper.selectQuoteStorageById(userId);
        if (quote == null) {
            throw new BaseException(UserConstant.NOT_FOUND_USER);
        }

        // 检查一下 maxStorageBytes 是否正常有确定值，没有就需要去查角色表来获取
        Long maxStorageBytes = quote.getMaxStorageBytes();
        if (maxStorageBytes == null) {
            maxStorageBytes = roleMapper.selectMaxStorageById(quote.getRoleId());
        }

        // 返回
        return new CachedStorageInfo(maxStorageBytes);
    }

    /**
     * 上传/修改后，基于实际使用量直接运算并写回存储。
     * <p>操作后不再查 DB，直接用 actualUsedBytes 运算后写回。</p>
     * @param actualUsedBytes 操作前通过 note+image 两表 sum 得到的实际使用量
     * @param maxBytes       最大配额（来自用户表或角色默认值）
     * @param deltaBytes     UPLOAD=文件大小，MODIFY=差量（可为负数）
     */
    private void syncStorageAfterUploadOrModify(Long userId, Object result,
                                                long actualUsedBytes, long maxBytes,
                                                StorageOperationType opType,
                                                long deltaBytes) {
        // 仅业务成功时才更新；失败时保持原值
        boolean businessSucceeded = !(result instanceof Result r) || r.getCode() == Result.SUCCESS;
        if (!businessSucceeded) {
            return;
        }

        // 计算新的使用量
        long newUsedBytes = actualUsedBytes + deltaBytes;
        // 存储量溢出检查
        if (newUsedBytes < 0) {
            log.error("Storage underflow, userId: {}", userId);
            throw new BaseException("存储量溢出！");
        }

        // 构建更新对象
        UserEntity updateUser = new UserEntity();
        updateUser.setId(userId);
        updateUser.setUsedStorageBytes(newUsedBytes);
        updateUser.setMaxStorageBytes(maxBytes);

        // 写回DB
        int count = userMapper.updateById(updateUser);
        if (count <= 0) {
            log.error("Failed to update user storage after upload/modify, userId: {}", userId);
            throw new BaseException(UserConstant.UPDATE_USER_STORAGE_FAILED);
        }
    }

    // ======================== 批量锁操作 ========================

    /**
     * 批量获取用户锁。
     * <p>在指定超时时间内依次获取所有用户的锁；任一获取失败则释放已获取的锁并返回 null。</p>
     * <p>每把锁最多等 300ms，避免单个用户锁阻塞导致整体超时。</p>
     * @return 成功返回已获取锁的用户 ID 列表（锁仍持有），失败返回 null（锁已释放）
     */
    private List<Long> acquireBatchLocks(Map<Long, Long> userStorageMap, String owner, long timeoutMillis) {
        long startTime = System.currentTimeMillis();
        List<Long> acquiredUserIds = new ArrayList<>(); // 获取成功的用户 ID 列表

        for (Long userId : userStorageMap.keySet()) {
            long remainingTime = timeoutMillis - (System.currentTimeMillis() - startTime);
            if (remainingTime <= 0) {
                releaseBatchLocks(acquiredUserIds, owner);
                return null;
            }

            String lockKey = getLockKey(userId);
            if (!lockOperator.tryLock(lockKey, owner, Math.min(remainingTime, 200))) {
                releaseBatchLocks(acquiredUserIds, owner);
                return null;
            }

            acquiredUserIds.add(userId);    // 获取成功，加入已获取锁的用户 ID 列表
        }

        // 返回已获取锁的用户 ID 列表
        return acquiredUserIds;
    }

    /**
     * 释放批量获取的用户锁。
     */
    private void releaseBatchLocks(List<Long> userIds, String owner) {
        for (Long userId : userIds) {
            lockOperator.releaseLock(getLockKey(userId), owner);
        }
    }

    // ======================== 锁管理 ========================

    private String getLockKey(Long userId) {
        return "StorageLock:User:" + userId;
    }

    private String getLockOwner() {
        return Thread.currentThread().getName() + ":" + Thread.currentThread().getId();
    }

    // ======================== 批量存储更新 ========================

    /**
     * 批量更新用户存储空间。
     * <p>使用 MyBatis 批量查询 + 批量 upsert，将网络 IO 从 O(n) 降为 O(1)。</p>
     * <p>Map 中的值为待释放的空间量（正值），更新时从 currentUsed 中减去。</p>
     */
    private void batchUpdateUserStorage(Map<Long, Long> userStorageMap) {
        List<Long> userIds = new ArrayList<>(userStorageMap.keySet());
        List<UserQuoteStorageDTO> userStorageList = userMapper.selectQuoteStorageByIds(userIds); // 批量查询

        // 构建 userId → 存储信息映射
        Map<Long, UserQuoteStorageDTO> infoMap = new HashMap<>();
        for (UserQuoteStorageDTO info : userStorageList) {
            infoMap.put(info.getId(), info);
        }

        // 计算每个用户的新存储值（减去已删除文件大小）
        List<UserEntity> updateList = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : userStorageMap.entrySet()) {
            Long userId = entry.getKey();
            Long deltaBytes = entry.getValue();

            UserQuoteStorageDTO info = infoMap.get(userId);
            if (info != null) {
                long currentUsed = info.getUsedStorageBytes() != null ? info.getUsedStorageBytes() : 0L;
                long newUsed = Math.max(0L, currentUsed - deltaBytes);  // 最小不能为 负数

                // 构建更新对象
                UserEntity user = new UserEntity();
                user.setId(userId);
                user.setUsedStorageBytes(newUsed);
                updateList.add(user);
            }
        }

        // 批量 upsert
        if (!updateList.isEmpty()) {
            int count = userMapper.batchUpsertStorage(updateList);
            if (count < updateList.size()) {
                log.error("Failed to batch upsert storage, count: {}, expected: {}",
                        count, updateList.size());
            }
        }
    }

    // ======================== 工具方法 ========================

    /**
     * 从 DB 实时计算用户实际使用的存储空间（笔记 + 图片）。
     */
    private long computeActualUsedStorage(Long userId) {
        Long noteBytes = noteMapper.sumNoteFileSizeByUserId(userId);
        Long imageBytes = imageMapper.sumImageFileSizeByUserId(userId);
        return (noteBytes == null ? 0L : noteBytes) + (imageBytes == null ? 0L : imageBytes);
    }

    /**
     * 解析操作所需的字节数。
     * <p>修改场景返回"新文件 - 旧文件"差量，普通上传返回完整文件大小。</p>
     */
    private long resolveRequiredBytes(ProceedingJoinPoint joinPoint,
                                      StorageHandler annotation,
                                      MultipartFile file) {
        long fileSize = file.getSize();
        if (annotation == null || annotation.operationType() != StorageOperationType.MODIFY) {
            return fileSize;
        }

        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Long noteId) {
                NoteEntity note = noteMapper.selectById(noteId);
                if (note != null && note.getMdFileSize() != null) {
                    return fileSize - note.getMdFileSize();
                }
            }
        }
        return fileSize;
    }

    /**
     * 从方法参数中提取 MultipartFile.
     */
    private MultipartFile extractMultipartFile(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof MultipartFile file) {
                return file;
            }
        }
        return null;
    }

    /**
     * 缓存的存储信息（仅用于获取 maxBytes）。
     */
    private record CachedStorageInfo(long maxBytes) {
    }
}
