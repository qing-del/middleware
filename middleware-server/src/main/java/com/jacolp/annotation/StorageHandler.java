package com.jacolp.annotation;

import com.jacolp.enums.StorageOperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 存储空间校验注解 - 校正一致性 + 判断配额是否足够 + 存储空间更新
 * <p>这个注解进入的切面类会 <b>开启事务</b> </p>
 * <p>需要保证后续目标方法中的 @Transactional 的 Propagation = REQUIRED</p>
 * <p>用了 DELETE / BATCH_DELETE 这两种方式的，最后都需要在 {@link com.jacolp.context.StorageUpdateContext} 中放置内容</p>
 * <p>{@link com.jacolp.context.StorageUpdateContext} 中存储的内容切面类有兜底机制释放 </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StorageHandler {
    StorageOperationType operationType();
}
