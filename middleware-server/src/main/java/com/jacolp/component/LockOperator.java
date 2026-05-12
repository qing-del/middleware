package com.jacolp.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * 锁操作类
 * <p>单机类不可重入锁</p>
 */
@Component
@Slf4j
public class LockOperator {

    // 存储锁的持有者
    private static final ConcurrentHashMap<String, String> LOCK_MAP = new ConcurrentHashMap<>();

    // 使用 ConcurrentLinkedQueue 作为并发等待队列，支持多个线程同时排队
    private static final ConcurrentHashMap<String, ConcurrentLinkedQueue<Thread>> WAIT_MAP = new ConcurrentHashMap<>();

    /**
     * 尝试获取锁
     * <p>这里不做等待，获取失败直接返回 false</p>
     * @param key   锁的 key
     * @param owner 锁的持有者
     * @return 是否成功获取锁
     */
    public boolean tryLock(String key, String owner) {
        return LOCK_MAP.putIfAbsent(key, owner) == null;
    }

    /**
     * 尝试获取锁
     * <p>这里会等待，获取失败会返回 false</p>
     * @param key   锁的 key
     * @param owner 锁的持有者
     * @param timeoutMillis 等待超时时间，单位毫秒
     * @return 是否成功获取锁
     */
    public boolean tryLock(String key, String owner, long timeoutMillis) {
        // 1. 首次尝试快速获取锁
        if (tryLock(key, owner)) {
            return true;
        }
        if (timeoutMillis <= 0) {
            return false;
        }

        long startTime = System.currentTimeMillis();
        Thread currentThread = Thread.currentThread();

        // 2. 将当前线程安全地加入该 key 的等待队列
        ConcurrentLinkedQueue<Thread> waitQueue = WAIT_MAP.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        waitQueue.add(currentThread);

        try {
            while (true) {
                // 被唤醒后再次尝试获取锁
                if (tryLock(key, owner)) {
                    return true;
                }

                // 计算剩余等待时间
                long remainingMillis = timeoutMillis - (System.currentTimeMillis() - startTime);
                if (remainingMillis <= 0) {
                    return false; // 已超时
                }

                // 3. 修复：parkNanos 必须将毫秒转换为纳秒
                LockSupport.parkNanos(this, TimeUnit.MILLISECONDS.toNanos(remainingMillis));

                // 4. 优化：响应线程中断，避免应用关闭或线程被打断时陷入死循环
                if (Thread.interrupted()) {
                    return false;
                }
            }
        } finally {
            // 5. 修复内存泄漏：无论成功、超时还是中断，都必须将自己从队列中移除
            waitQueue.remove(currentThread);

            // 优化：如果队列空了，顺手清理掉 map 中的 key
            if (waitQueue.isEmpty()) {
                WAIT_MAP.remove(key, waitQueue);
            }
        }
    }

    /**
     * 释放锁
     * @param key   锁的 key
     * @param owner 锁的持有者
     * @return 是否成功释放锁
     */
    public boolean releaseLock(String key, String owner) {
        // 6. 修复并发安全：利用 ConcurrentHashMap 提供的原子方法 remove(key, value)
        if (LOCK_MAP.remove(key, owner)) {

            // 获取等待队列并唤醒排在前面的有效线程
            ConcurrentLinkedQueue<Thread> waitQueue = WAIT_MAP.get(key);
            if (waitQueue != null) {
                for (Thread thread : waitQueue) {
                    if (thread != null && thread.isAlive()) {
                        LockSupport.unpark(thread);
                        break; // 每次只唤醒一个线程去争抢锁
                    }
                }
            }
            return true;
        }

        // 使用 warn，因为释放非自己持有的锁可能只是业务逻辑重复调用
        log.warn("releaseLock failed! Key not found or not owned by current owner. Key: {}, Owner: {}", key, owner);
        return false;
    }
}