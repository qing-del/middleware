package com.jacolp.middleware.messaging;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class OutboxRepository {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public OutboxRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public void insert(EventEnvelope envelope, String routingKey, String serializedEnvelope) {
        jdbcTemplate.update("""
                INSERT INTO sys_event_outbox
                    (event_id, event_type, routing_key, payload, status, retry_count,
                     next_retry_time, create_time)
                VALUES (?, ?, ?, ?, 'PENDING', 0, NOW(), NOW())
                """, envelope.eventId(), envelope.eventType(), routingKey, serializedEnvelope);
    }

    public List<OutboxRecord> claimBatch(String claimant, int limit, long claimSeconds) {
        List<OutboxRecord> claimed = transactionTemplate.execute(status -> {
            jdbcTemplate.update("""
                    UPDATE sys_event_outbox
                    SET status = 'RETRY', claimed_by = NULL, claim_until = NULL,
                        next_retry_time = NOW()
                    WHERE status = 'PROCESSING' AND claim_until < NOW()
                    """);
            List<OutboxRecord> rows = jdbcTemplate.query("""
                    SELECT id, event_id, event_type, routing_key, payload, retry_count, create_time
                    FROM sys_event_outbox
                    WHERE status IN ('PENDING', 'RETRY') AND next_retry_time <= NOW()
                    ORDER BY id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                    """, OutboxRepository::mapRecord, limit);
            if (rows.isEmpty()) {
                return List.of();
            }
            String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));
            Object[] parameters = new Object[rows.size() + 2];
            parameters[0] = claimant;
            parameters[1] = claimSeconds;
            for (int i = 0; i < rows.size(); i++) {
                parameters[i + 2] = rows.get(i).id();
            }
            jdbcTemplate.update("""
                    UPDATE sys_event_outbox
                    SET status = 'PROCESSING', claimed_by = ?,
                        claim_until = DATE_ADD(NOW(), INTERVAL ? SECOND)
                    WHERE id IN (%s)
                    """.formatted(placeholders), parameters);
            return rows;
        });
        return claimed == null ? List.of() : claimed;
    }

    public boolean markPublished(long id, String claimant) {
        return jdbcTemplate.update("""
                UPDATE sys_event_outbox
                SET status = 'PUBLISHED', published_time = NOW(), claimed_by = NULL,
                    claim_until = NULL, last_error = NULL
                WHERE id = ? AND status = 'PROCESSING' AND claimed_by = ?
                """, id, claimant) == 1;
    }

    public boolean markFailed(long id, String claimant, int retryCount, LocalDateTime nextRetry,
                              String error, boolean dead) {
        return jdbcTemplate.update("""
                UPDATE sys_event_outbox
                SET status = ?, retry_count = ?, next_retry_time = ?, last_error = ?,
                    claimed_by = NULL, claim_until = NULL
                WHERE id = ? AND status = 'PROCESSING' AND claimed_by = ?
                """, dead ? "DEAD" : "RETRY", retryCount, nextRetry, truncate(error), id, claimant) == 1;
    }

    private static OutboxRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxRecord(rs.getLong("id"), rs.getString("event_id"), rs.getString("event_type"),
                rs.getString("routing_key"), rs.getString("payload"), rs.getInt("retry_count"),
                rs.getTimestamp("create_time").toLocalDateTime());
    }

    private static String truncate(String error) {
        if (error == null) return null;
        return error.length() <= 2000 ? error : error.substring(0, 2000);
    }
}
