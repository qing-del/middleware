package com.jacolp.document.infrastructure.redis;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

class DocumentRedisRepositoryTest {

    private RedisConnectionFactory connectionFactory;
    private RedisConnection connection;
    private DocumentRedisRepository repository;

    @BeforeEach
    void setUp() {
        connectionFactory = mock(RedisConnectionFactory.class);
        connection = mock(RedisConnection.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        repository = new DocumentRedisRepository(connectionFactory);
    }

    @Test
    void shouldPersistRoomMetaInDocumentHash() {
        DocumentRoomMeta meta = new DocumentRoomMeta(18L, 7L, false, null, 1234L, 9L);

        repository.saveRoomMeta(meta);

        ArgumentCaptor<byte[]> key = ArgumentCaptor.forClass(byte[].class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<byte[], byte[]>> fields = ArgumentCaptor.forClass(Map.class);
        verify(connection).hMSet(key.capture(), fields.capture());
        assertThat(string(key.getValue())).isEqualTo("document:meta:18");
        assertThat(stringMap(fields.getValue())).containsExactlyInAnyOrderEntriesOf(Map.of(
                "documentId", "18",
                "ownerUserId", "7",
                "isClose", "0",
                "lastModifyTime", "1234",
                "lastModifyUserId", "9"));
        verify(connection, times(2)).hDel(any(byte[].class), any(byte[].class));
        verify(connection).close();
    }

    @Test
    void shouldReadRoomMetaFromDocumentHash() {
        when(connection.hGetAll(any(byte[].class))).thenReturn(bytesMap(Map.of(
                "documentId", "18",
                "ownerUserId", "7",
                "isClose", "1",
                "closeToken", "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                "lastModifyTime", "1234",
                "lastModifyUserId", "9")));

        assertThat(repository.findRoomMeta(18L)).contains(new DocumentRoomMeta(
                18L, 7L, true, "3fa85f64-5717-4562-b3fc-2c963f66afa6", 1234L, 9L));

        verify(connection).close();
    }

    @Test
    void shouldReadLegacyTeamIdRoomMetaDuringRedisRollout() {
        when(connection.hGetAll(any(byte[].class))).thenReturn(bytesMap(Map.of(
                "documentId", "18",
                "teamId", "7",
                "isClose", "0",
                "lastModifyTime", "1234",
                "lastModifyUserId", "9")));

        assertThat(repository.findRoomMeta(18L)).contains(new DocumentRoomMeta(
                18L, 7L, false, null, 1234L, 9L));
    }

    @Test
    void shouldAppendUnmodifiedBinaryUpdateAndReturnRedisId() {
        when(connection.xAdd(any(byte[].class), any())).thenReturn(RecordId.of("1234-0"));
        byte[] updateData = {(byte) 0x80, 0, 1};

        String redisOpId = repository.appendPendingUpdate(new DocumentPendingUpdate(
                18L, updateData, "3fa85f64-5717-4562-b3fc-2c963f66afa6", 9L, "USER", 1234L));

        ArgumentCaptor<byte[]> key = ArgumentCaptor.forClass(byte[].class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<byte[], byte[]>> fields = ArgumentCaptor.forClass(Map.class);
        verify(connection).xAdd(key.capture(), fields.capture());
        assertThat(redisOpId).isEqualTo("1234-0");
        assertThat(string(key.getValue())).isEqualTo("document:updates:18");
        assertThat(findField(fields.getValue(), "update")).containsExactly((byte) 0x80, 0, 1);
        assertThat(stringMap(fields.getValue())).containsEntry("clientUpdateId", "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                .containsEntry("operatorId", "9")
                .containsEntry("operatorType", "USER")
                .containsEntry("createdAt", "1234");
        verify(connection).close();
    }

    @Test
    void shouldReadAndDeletePendingUpdatesByRedisStreamId() {
        byte[] key = "document:updates:18".getBytes(UTF_8);
        ByteRecord record = StreamRecords.rawBytes(bytesMap(Map.of(
                        "update", "\u0001\u0000\u0002",
                        "clientUpdateId", "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                        "operatorId", "9",
                        "operatorType", "USER",
                        "createdAt", "1234")))
                .withStreamKey(key)
                .withId(RecordId.of("1234-0"));
        when(connection.xRange(any(byte[].class), any(), any())).thenReturn(List.of(record));
        when(connection.xDel(any(byte[].class), any(RecordId[].class))).thenReturn(1L);

        List<StoredDocumentPendingUpdate> updates = repository.readPendingUpdates(18L, 10);
        long deleted = repository.deletePendingUpdates(18L, List.of("1234-0"));

        assertThat(updates).hasSize(1);
        assertThat(updates.getFirst().redisOpId()).isEqualTo("1234-0");
        assertThat(updates.getFirst().update().updateData()).containsExactly(1, 0, 2);
        assertThat(deleted).isEqualTo(1L);
        verify(connection, times(2)).close();
    }

    @Test
    void shouldExposeStableDocumentKeyNames() {
        assertThat(DocumentRedisRepository.roomMetaKey(18L)).isEqualTo("document:meta:18");
        assertThat(DocumentRedisRepository.pendingUpdatesKey(18L)).isEqualTo("document:updates:18");
    }

    @Test
    void shouldCountAndScanOnlyDocumentRuntimeKeysForRecovery() {
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);
        byte[] metaKey = "document:meta:18".getBytes(UTF_8);
        when(connection.xLen(any(byte[].class))).thenReturn(3L);
        when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(metaKey);
        when(connection.hGetAll(metaKey)).thenReturn(bytesMap(Map.of(
                "documentId", "18",
                "ownerUserId", "7",
                "isClose", "0",
                "lastModifyTime", "1234",
                "lastModifyUserId", "9")));

        assertThat(repository.pendingUpdateCount(18L)).isEqualTo(3L);
        assertThat(repository.findRoomMetas()).containsExactly(new DocumentRoomMeta(18L, 7L,
                false, null, 1234L, 9L));
        verify(cursor).close();
    }

    @Test
    void shouldMaintainEphemeralPresenceAndDeleteOnlyRoomRuntimeKeys() {
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);
        when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("document:presence:18:node:session".getBytes(UTF_8));

        repository.savePresence("document:presence:18:node:session", 60_000L);
        repository.deletePresence("document:presence:18:node:session");
        assertThat(repository.countPresence(18L)).isEqualTo(1L);
        repository.deleteRoomRuntime(18L);

        verify(connection).set(any(byte[].class), any(byte[].class), any(), any());
        verify(connection).del("document:presence:18:node:session".getBytes(UTF_8));
        verify(connection).del("document:meta:18".getBytes(UTF_8), "document:updates:18".getBytes(UTF_8));
    }

    private static Map<byte[], byte[]> bytesMap(Map<String, String> values) {
        Map<byte[], byte[]> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key.getBytes(UTF_8), value.getBytes(UTF_8)));
        return result;
    }

    private static Map<String, String> stringMap(Map<byte[], byte[]> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(string(key), string(value)));
        return result;
    }

    private static byte[] findField(Map<byte[], byte[]> values, String fieldName) {
        return values.entrySet().stream()
                .filter(entry -> string(entry.getKey()).equals(fieldName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
    }

    private static String string(byte[] value) {
        return new String(value, UTF_8);
    }
}
