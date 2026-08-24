package com.jacolp.document.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Repository;

/**
 * 以二进制安全方式保存文档 Room 元数据和待持久化的 Yjs 更新。
 *
 * <p>这里刻意不使用 {@code StringRedisTemplate}：Yjs 更新是任意二进制数据，
 * 写入 Redis Stream 时必须保持字节完全不变。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentRedisRepository {

    private static final byte[] FIELD_DOCUMENT_ID = bytes("documentId");
    private static final byte[] FIELD_TEAM_ID = bytes("teamId");
    private static final byte[] FIELD_IS_CLOSE = bytes("isClose");
    private static final byte[] FIELD_CLOSE_TOKEN = bytes("closeToken");
    private static final byte[] FIELD_LAST_MODIFY_TIME = bytes("lastModifyTime");
    private static final byte[] FIELD_LAST_MODIFY_USER_ID = bytes("lastModifyUserId");
    private static final byte[] FIELD_UPDATE = bytes("update");
    private static final byte[] FIELD_CLIENT_UPDATE_ID = bytes("clientUpdateId");
    private static final byte[] FIELD_OPERATOR_ID = bytes("operatorId");
    private static final byte[] FIELD_OPERATOR_TYPE = bytes("operatorType");
    private static final byte[] FIELD_CREATED_AT = bytes("createdAt");

    private final RedisConnectionFactory redisConnectionFactory;

    public DocumentRedisRepository(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    public void saveRoomMeta(DocumentRoomMeta meta) {
        Map<byte[], byte[]> fields = new LinkedHashMap<>();
        fields.put(FIELD_DOCUMENT_ID, bytes(meta.documentId()));
        fields.put(FIELD_TEAM_ID, bytes(meta.teamId()));
        fields.put(FIELD_IS_CLOSE, bytes(meta.closeRequested() ? 1 : 0));
        fields.put(FIELD_LAST_MODIFY_TIME, bytes(meta.lastModifyTime()));
        if (meta.lastModifyUserId() != null) {
            fields.put(FIELD_LAST_MODIFY_USER_ID, bytes(meta.lastModifyUserId()));
        }
        if (meta.closeToken() != null) {
            fields.put(FIELD_CLOSE_TOKEN, bytes(meta.closeToken()));
        }

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            byte[] key = bytes(roomMetaKey(meta.documentId()));
            connection.hMSet(key, fields);
            if (meta.lastModifyUserId() == null) {
                connection.hDel(key, FIELD_LAST_MODIFY_USER_ID);
            }
            if (meta.closeToken() == null) {
                connection.hDel(key, FIELD_CLOSE_TOKEN);
            }
        }
    }

    public Optional<DocumentRoomMeta> findRoomMeta(long documentId) {
        requirePositive(documentId, "documentId");
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Map<byte[], byte[]> rawFields = connection.hGetAll(bytes(roomMetaKey(documentId)));
            if (rawFields == null || rawFields.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(toRoomMeta(rawFields));
        }
    }

    /** 追加一条二进制更新，并在 XADD 成功后返回对应的 Redis Stream ID。 */
    public String appendPendingUpdate(DocumentPendingUpdate update) {
        Map<byte[], byte[]> fields = new LinkedHashMap<>();
        fields.put(FIELD_UPDATE, update.updateData());
        fields.put(FIELD_CLIENT_UPDATE_ID, bytes(update.clientUpdateId()));
        if (update.operatorId() != null) {
            fields.put(FIELD_OPERATOR_ID, bytes(update.operatorId()));
        }
        fields.put(FIELD_OPERATOR_TYPE, bytes(update.operatorType()));
        fields.put(FIELD_CREATED_AT, bytes(update.createdAt()));

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            RecordId recordId = connection.xAdd(bytes(pendingUpdatesKey(update.documentId())), fields);
            if (recordId == null || recordId.getValue() == null || recordId.getValue().isBlank()) {
                throw new IllegalStateException("Redis XADD did not return a stream entry ID");
            }
            return recordId.getValue();
        }
    }

    public List<StoredDocumentPendingUpdate> readPendingUpdates(long documentId, int maxCount) {
        requirePositive(documentId, "documentId");
        if (maxCount <= 0) {
            throw new IllegalArgumentException("maxCount must be positive");
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            List<ByteRecord> records = connection.xRange(
                    bytes(pendingUpdatesKey(documentId)), Range.unbounded(), Limit.limit().count(maxCount));
            if (records == null || records.isEmpty()) {
                return List.of();
            }
            List<StoredDocumentPendingUpdate> updates = new ArrayList<>(records.size());
            for (ByteRecord record : records) {
                updates.add(toStoredPendingUpdate(documentId, record));
            }
            return List.copyOf(updates);
        }
    }

    public long deletePendingUpdates(long documentId, Collection<String> redisOpIds) {
        requirePositive(documentId, "documentId");
        if (redisOpIds == null || redisOpIds.isEmpty()) {
            return 0;
        }
        RecordId[] recordIds = redisOpIds.stream().map(DocumentRedisRepository::toRecordId)
                .toArray(RecordId[]::new);
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Long deleted = connection.xDel(bytes(pendingUpdatesKey(documentId)), recordIds);
            return deleted == null ? 0 : deleted;
        }
    }

    /** 返回一个文档当前尚未落库的 Redis Stream 条目数。 */
    public long pendingUpdateCount(long documentId) {
        requirePositive(documentId, "documentId");
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Long count = connection.xLen(bytes(pendingUpdatesKey(documentId)));
            return count == null ? 0L : count;
        }
    }

    /** 扫描恢复调度器使用的小型活跃 Room 键空间。 */
    public List<DocumentRoomMeta> findRoomMetas() {
        List<DocumentRoomMeta> metas = new ArrayList<>();
        try (RedisConnection connection = redisConnectionFactory.getConnection();
             Cursor<byte[]> keys = connection.scan(ScanOptions.scanOptions().match("document:meta:*").count(100).build())) {
            while (keys.hasNext()) {
                Map<byte[], byte[]> fields = connection.hGetAll(keys.next());
                if (fields != null && !fields.isEmpty()) {
                    metas.add(toRoomMeta(fields));
                }
            }
        }
        return List.copyOf(metas);
    }

    /** 创建或续期跨实例可见的临时会话在线租约。 */
    public void savePresence(String presenceKey, long ttlMs) {
        if (presenceKey == null || presenceKey.isBlank()) {
            throw new IllegalArgumentException("presenceKey must not be blank");
        }
        if (ttlMs <= 0) {
            throw new IllegalArgumentException("ttlMs must be positive");
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.set(bytes(presenceKey), bytes("1"), Expiration.milliseconds(ttlMs), SetOption.UPSERT);
        }
    }

    public void deletePresence(String presenceKey) {
        if (presenceKey == null || presenceKey.isBlank()) {
            return;
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.del(bytes(presenceKey));
        }
    }

    /** 统计所有 core 实例上一个文档尚未过期的会话在线租约。 */
    public long countPresence(long documentId) {
        requirePositive(documentId, "documentId");
        long count = 0L;
        try (RedisConnection connection = redisConnectionFactory.getConnection();
             Cursor<byte[]> keys = connection.scan(ScanOptions.scanOptions()
                     .match("document:presence:" + documentId + ":*").count(100).build())) {
            while (keys.hasNext()) {
                keys.next();
                count++;
            }
        }
        return count;
    }

    /** 最终持久化后只清除 Redis 运行时状态；会话在线租约需要单独校验。 */
    public void deleteRoomRuntime(long documentId) {
        requirePositive(documentId, "documentId");
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.del(bytes(roomMetaKey(documentId)), bytes(pendingUpdatesKey(documentId)));
        }
    }

    static String roomMetaKey(long documentId) {
        requirePositive(documentId, "documentId");
        return "document:meta:" + documentId;
    }

    static String pendingUpdatesKey(long documentId) {
        requirePositive(documentId, "documentId");
        return "document:updates:" + documentId;
    }

    private static DocumentRoomMeta toRoomMeta(Map<byte[], byte[]> rawFields) {
        Map<String, byte[]> fields = stringFields(rawFields);
        return new DocumentRoomMeta(
                parseRequiredLong(fields, "documentId"),
                parseRequiredLong(fields, "teamId"),
                parseCloseRequested(fields),
                parseOptionalString(fields, "closeToken"),
                parseRequiredLong(fields, "lastModifyTime"),
                parseOptionalLong(fields, "lastModifyUserId"));
    }

    private static StoredDocumentPendingUpdate toStoredPendingUpdate(long documentId, ByteRecord record) {
        if (record.getId() == null || record.getId().getValue() == null || record.getId().getValue().isBlank()) {
            throw new IllegalStateException("Redis Stream record is missing its ID");
        }
        Map<String, byte[]> fields = stringFields(record.getValue());
        byte[] updateData = fields.get("update");
        if (updateData == null || updateData.length == 0) {
            throw new IllegalStateException("Redis Stream record is missing a binary update");
        }
        return new StoredDocumentPendingUpdate(record.getId().getValue(), new DocumentPendingUpdate(
                documentId,
                updateData,
                requiredString(fields, "clientUpdateId"),
                parseOptionalLong(fields, "operatorId"),
                requiredString(fields, "operatorType"),
                parseRequiredLong(fields, "createdAt")));
    }

    private static Map<String, byte[]> stringFields(Map<byte[], byte[]> rawFields) {
        Map<String, byte[]> fields = new LinkedHashMap<>();
        rawFields.forEach((key, value) -> fields.put(string(key), value));
        return fields;
    }

    private static boolean parseCloseRequested(Map<String, byte[]> fields) {
        String value = requiredString(fields, "isClose");
        return switch (value) {
            case "0" -> false;
            case "1" -> true;
            default -> throw new IllegalStateException("Redis room meta has an invalid isClose value");
        };
    }

    private static long parseRequiredLong(Map<String, byte[]> fields, String fieldName) {
        try {
            return Long.parseLong(requiredString(fields, fieldName));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Redis field " + fieldName + " must be a long", exception);
        }
    }

    private static Long parseOptionalLong(Map<String, byte[]> fields, String fieldName) {
        String value = parseOptionalString(fields, fieldName);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Redis field " + fieldName + " must be a long", exception);
        }
    }

    private static String requiredString(Map<String, byte[]> fields, String fieldName) {
        String value = parseOptionalString(fields, fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Redis field " + fieldName + " is required");
        }
        return value;
    }

    private static String parseOptionalString(Map<String, byte[]> fields, String fieldName) {
        byte[] value = fields.get(fieldName);
        return value == null ? null : string(value);
    }

    private static RecordId toRecordId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("redisOpId must not be blank");
        }
        return RecordId.of(value);
    }

    private static void requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static byte[] bytes(Object value) {
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    private static String string(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
