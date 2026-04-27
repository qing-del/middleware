package com.jacolp.context;

import java.util.Map;

/**
 * 存储空间更新上下文
 * <p>需要存放 Map<UserId, FileSize> 这样的格式</p>
 * <p>- FileSize 代表释放配额的大小（删除文件的大小）</p>
 */
public class StorageUpdateContext {
    private static final ThreadLocal<Map<Long, Long>> USER_STORAGE_MAP = new ThreadLocal<>();

    public static void setStorageMap(Map<Long, Long> map) {
        USER_STORAGE_MAP.set(map);
    }

    public static Map<Long, Long> getStorageMap() {
        return USER_STORAGE_MAP.get();
    }

    public static void clear() {
        USER_STORAGE_MAP.remove();
    }
}