package com.jacolp.aspect;

import com.jacolp.constant.ImageConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.NoteMapper;
import com.jacolp.mapper.RoleMapper;
import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.domain.UserQuoteStorageDO;
import com.jacolp.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.jacolp.annotation.CheckAndUpdateUserStorage;
import com.jacolp.context.BaseContext;
import com.jacolp.mapper.ImageMapper;
import com.jacolp.pojo.entity.UserEntity;

import java.util.Hashtable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 存储空间校验切面 - 校正一致性 + 判断配额是否足够 + 更新用户存储空间
 * Order=2，在 ImageLimitAspect 后执行。
 */
@Aspect
@Component
@Order(2)
@Slf4j
public class CheckAndUpdateUserStorageAspect {
    // 总锁
    private final ReentrantLock lock = new ReentrantLock();
    // 用户id锁
    private final Hashtable<Long, ReentrantLock> lockMap = new Hashtable<>();


    @Autowired private ImageMapper imageMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private NoteMapper noteMapper;
    @Autowired private RoleMapper roleMapper;

    @Pointcut("@annotation(checkAndUpdateUserStorage)")
    public void checkAndUpdateUserStoragePointcut(CheckAndUpdateUserStorage checkAndUpdateUserStorage) {
    }

    /**
     * 检查并更新用户的存储空间
     * @param joinPoint
     * @param checkAndUpdateUserStorage
     * @return
     * @throws Throwable
     */
    @Around("checkAndUpdateUserStoragePointcut(checkAndUpdateUserStorage)")
    public Object checkAndUpdateUserStorage(ProceedingJoinPoint joinPoint,
                                            CheckAndUpdateUserStorage checkAndUpdateUserStorage) throws Throwable {
        // 获取 userId
        Long userId = BaseContext.getCurrentId();

        // 查找 MultipartFile 参数获取文件大小
        Object[] args = joinPoint.getArgs();
        MultipartFile file = null;
        for (Object arg : args) {
            if (arg instanceof MultipartFile) {
                file = (MultipartFile) arg;
                break;
            }
        }

        if (file == null || file.isEmpty()) {
            // 没有文件参数，直接放行
            return joinPoint.proceed();
        }

        long requiredBytes = file.getSize();

        // 获取用户的存储配额信息
        UserQuoteStorageDO userQuoteStorageDO = userMapper.selectQuoteStorageById(userId);
        if (userQuoteStorageDO == null) {
            throw new BaseException(UserConstant.NOT_FIND_USER);
        }


        // 多端上传的时候需要加锁，防止后续后置导致数据一致性不同
        ReentrantLock userLock = getLock(userId);
        try {
            userLock.lock();    // 使用用户锁来上锁

            // 从数据库实时计算实际使用的存储空间（两张表）
            Long actualNoteUsedStorageBytes = noteMapper.sumNoteFileSizeByUserId(userId);
            Long actualImageUsedStorageBytes = imageMapper.sumImageFileSizeByUserId(userId);
            Long maxStorageBytes = userQuoteStorageDO.getMaxStorageBytes();
            // 对其进行保底初始化
            actualNoteUsedStorageBytes = actualNoteUsedStorageBytes == null ? 0L : actualNoteUsedStorageBytes;
            actualImageUsedStorageBytes = actualImageUsedStorageBytes == null ? 0L : actualImageUsedStorageBytes;
            Long actualUsedStorageBytes = actualNoteUsedStorageBytes + actualImageUsedStorageBytes; // 计算总的消耗量

            // 这里获取的是从用户表从查询出来的用户存储空间
            Long cachedUsedStorageBytes = userQuoteStorageDO.getUsedStorageBytes() == null ?
                    0L : userQuoteStorageDO.getUsedStorageBytes();
            // 如果不存在则获取用户的角色的存储空间作为默认最大存储空间
            maxStorageBytes = maxStorageBytes == null ?
                    roleMapper.selectMaxStorageById(userQuoteStorageDO.getRoleId()) : maxStorageBytes;

            // 一致性校正：如果实际值与缓存值不匹配，更新数据库
            boolean needUpdate = false;
            if (!cachedUsedStorageBytes.equals(actualUsedStorageBytes) ||
                    !maxStorageBytes.equals(userQuoteStorageDO.getMaxStorageBytes())) {
                needUpdate = true;
            }

            // 如果需要更新，执行更新操作
            if (needUpdate) {
                cachedUsedStorageBytes = actualUsedStorageBytes;
            }

            long totalUsedBytes = cachedUsedStorageBytes;
            if (totalUsedBytes + requiredBytes > maxStorageBytes) {
                throw new BaseException(ImageConstant.IMAGE_STORAGE_QUOTA_EXCEEDED);
            }

            // 校验通过，继续执行
            Object result = joinPoint.proceed();


            boolean shouldSyncAfterProceed = true;
            if (result instanceof Result) {
                shouldSyncAfterProceed = ((Result<?>) result).getCode() == Result.SUCCESS;
            }

            if (shouldSyncAfterProceed || needUpdate) {
                // 这两个语句返回来必定有值
                Long lastestNoteUsedStorageBytes = noteMapper.sumNoteFileSizeByUserId(userId);
                Long lastestImageUsedStorageBytes = imageMapper.sumImageFileSizeByUserId(userId);

                // 最后计算加入新文件之后的存储使用量
                Long latestUsedStorageBytes = lastestImageUsedStorageBytes + lastestNoteUsedStorageBytes;

                UserEntity updateUser = new UserEntity();
                updateUser.setId(userId);
                updateUser.setUsedStorageBytes(latestUsedStorageBytes);
                updateUser.setMaxStorageBytes(maxStorageBytes);
                int count = userMapper.updateById(updateUser);
                if (count <= 0) {
                    log.error("Failed to update user storage!");
                    throw new BaseException(UserConstant.UPDATE_USER_STORAGE_FAILED);
                }
            }

            // 返回结果
            return result;
        } finally {
            userLock.unlock();
        }
    }


    /**
     * 获取用户锁
     * 采用DCL策略
     * @param userId
     * @return
     */
    private ReentrantLock getLock(Long userId) {
        ReentrantLock userLock = lockMap.get(userId);
        if (userLock == null) {
            try{
                lock.lock();    // 上总锁
                if ((userLock = lockMap.get(userId)) == null) { // 二次检查防止别的线程创建了不知道
                    userLock = new ReentrantLock();
                    lockMap.put(userId, userLock);
                }


                return userLock;
            } finally {
                lock.unlock();  // 解开总锁
            }
        }

        return userLock;
    }

    /**
     * 清空锁表
     * 虽然在map先删除了锁，才在后续解锁
     * 但是实际上GC在检测是否可抵达的时候，也会发现这个节点无法抵达
     * 最后将其回收
     */
    public void clearLockMap() {
        lockMap.forEach((userId, userLock) -> {
            boolean isLocked = false;   // 初始化是没上锁
            try{
                isLocked = userLock.tryLock(100, TimeUnit.MILLISECONDS);    // 尝试获取用户锁先
                if (isLocked) {
                    // 获取成功说明这个用户锁是没有人在里面的，可以删除
                    lockMap.remove(userId);     // 移除锁
                }
            } catch (InterruptedException e) {
                e.fillInStackTrace();
            } finally {
                if(isLocked) userLock.unlock();      // 如果上了锁就要解锁
            }
        });
    }
}
