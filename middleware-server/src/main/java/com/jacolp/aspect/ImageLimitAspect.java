package com.jacolp.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.jacolp.annotation.ImageLimit;
import com.jacolp.constant.ImageConstant;
import com.jacolp.exception.BaseException;

/**
 * 图片上传校验切面 - 文件大小 + 后缀格式校验。
 * Order=1，在 CheckAndUpdateUserStorageAspect 前执行。
 */
@Aspect
@Component
@Order(1)
public class ImageLimitAspect {

    @Pointcut("@annotation(imageLimit)")
    public void imageLimitPointcut(ImageLimit imageLimit) {
    }

    @Around("imageLimitPointcut(imageLimit)")
    public Object checkImageLimit(ProceedingJoinPoint joinPoint, ImageLimit imageLimit) throws Throwable {
        // 查找 MultipartFile 参数
        Object[] args = joinPoint.getArgs();
        MultipartFile file = null;
        
        for (Object arg : args) {
            if (arg instanceof MultipartFile) {
                file = (MultipartFile) arg;
                break;
            }
        }

        // 文件为空校验
        if (file == null || file.isEmpty()) {
            throw new BaseException(ImageConstant.IMAGE_FILE_EMPTY);
        }

        // 文件大小校验
        if (file.getSize() > imageLimit.maxBytes()) {
            throw new BaseException(ImageConstant.IMAGE_FILE_TOO_LARGE);
        }

        // 文件后缀校验
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new BaseException(ImageConstant.IMAGE_INVALID_FORMAT);
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        boolean isValidFormat = false;
        for (String format : ImageConstant.ALLOWED_IMAGE_FORMATS) {
            if (format.equals(extension)) {
                isValidFormat = true;
                break;
            }
        }

        if (!isValidFormat) {
            throw new BaseException(ImageConstant.IMAGE_INVALID_FORMAT);
        }

        // 校验通过，继续执行
        return joinPoint.proceed();
    }
}
