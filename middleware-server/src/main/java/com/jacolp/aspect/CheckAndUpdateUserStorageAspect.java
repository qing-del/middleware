package com.jacolp.aspect;

import com.jacolp.constant.ImageConstant;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.NoteMapper;
import com.jacolp.mapper.RoleMapper;
import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.domain.UserQuoteStorageDO;
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

/**
 * 存储空间校验切面 - 校正一致性 + 判断配额是否足够。
 * Order=2，在 ImageLimitAspect 后执行。
 */
@Aspect
@Component
@Order(2)
public class CheckAndUpdateUserStorageAspect {

    @Autowired private ImageMapper imageMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private NoteMapper noteMapper;
    @Autowired private RoleMapper roleMapper;

    @Pointcut("@annotation(checkAndUpdateUserStorage)")
    public void checkAndUpdateUserStoragePointcut(CheckAndUpdateUserStorage checkAndUpdateUserStorage) {
    }

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
            throw new BaseException("用户信息不存在");
        }

        // 从数据库实时计算实际使用的存储空间
        Long actualNoteUsedStorageBytes = noteMapper.sumNoteFileSizeByUserId(userId);
        Long actualImageUsedStorageBytes = imageMapper.sumImageFileSizeByUserId(userId);
        Long maxStorageBytes = userQuoteStorageDO.getMaxStorageBytes();
        // 对其进行保底初始化
        actualNoteUsedStorageBytes = actualNoteUsedStorageBytes == null ? 0L : actualNoteUsedStorageBytes;
        actualImageUsedStorageBytes = actualImageUsedStorageBytes == null ? 0L : actualImageUsedStorageBytes;
        maxStorageBytes = maxStorageBytes == null ?
                roleMapper.selectMaxStorageById(userQuoteStorageDO.getRoleId()) : maxStorageBytes;

        // 一致性校正：如果实际值与缓存值不匹配，更新数据库
        boolean needUpdate = false;
        if (!actualNoteUsedStorageBytes.equals(userQuoteStorageDO.getNoteUsedStorageBytes())) {
            needUpdate = true;
        }
        if (!actualImageUsedStorageBytes.equals(userQuoteStorageDO.getImageUsedStorageBytes())) {
            needUpdate = true;
        }

        if (userQuoteStorageDO.getMaxStorageBytes() == null || userQuoteStorageDO.getMaxStorageBytes() < 0) {
            needUpdate = true;
        }

        // 如果需要更新，执行更新操作
        if (needUpdate) {
            UserEntity updateUser = new UserEntity();
            updateUser.setId(userId);
            updateUser.setNoteUsedStorageBytes(actualNoteUsedStorageBytes);
            updateUser.setImageUsedStorageBytes(actualImageUsedStorageBytes);
            updateUser.setMaxStorageBytes(maxStorageBytes);
            userMapper.updateById(updateUser);
            
            // 更新本地缓存对象
            userQuoteStorageDO.setNoteUsedStorageBytes(actualNoteUsedStorageBytes);
            userQuoteStorageDO.setImageUsedStorageBytes(actualImageUsedStorageBytes);
            userQuoteStorageDO.setMaxStorageBytes(maxStorageBytes);
        }


        long totalUsedBytes = userQuoteStorageDO.getNoteUsedStorageBytes() + userQuoteStorageDO.getImageUsedStorageBytes();
        if (totalUsedBytes + requiredBytes > userQuoteStorageDO.getMaxStorageBytes()) {
            throw new BaseException(ImageConstant.IMAGE_STORAGE_QUOTA_EXCEEDED);
        }
        
        // 校验通过，继续执行
        return joinPoint.proceed();
    }
}
