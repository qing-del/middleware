package com.jacolp.annotation;

import com.jacolp.enums.StorageOperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 存储空间校验注解 - 校正一致性 + 判断配额是否足够。
 * <p>这个注解进入的切面类会 <b>开启事务</b> </p>
 * <p>需要保证后续目标方法中的 @Transactional 的 Propagation = REQUIRED</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckAndUpdateUserStorage {
    StorageOperationType operationType();
}
