package com.jacolp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 图片上传校验注解 - 文件大小 + 后缀格式校验。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ImageLimit {
    
    /**
     * 允许的最大文件大小（字节），默认 2MB。
     */
    long maxBytes() default 2 * 1024 * 1024;
}
