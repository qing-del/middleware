package com.jacolp.middleware.messaging;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsyncCommandStateService {
    private final JdbcTemplate jdbcTemplate;

    public AsyncCommandStateService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryBegin(String owner, String aggregateType, long aggregateId,
                            String commandId, String commandType) {
        int reopened = jdbcTemplate.update("""
                UPDATE sys_async_command_state
                SET command_id = ?, command_type = ?, state = 'PENDING', update_time = NOW()
                WHERE owner_module = ? AND aggregate_type = ? AND aggregate_id = ? AND state <> 'PENDING'
                """, commandId, commandType, owner, aggregateType, aggregateId);
        if (reopened == 1) return true;
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_async_command_state
                        (owner_module, aggregate_type, aggregate_id, command_id, command_type, state, update_time)
                    VALUES (?, ?, ?, ?, ?, 'PENDING', NOW())
                    """, owner, aggregateType, aggregateId, commandId, commandType);
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean completeIfCurrent(String owner, String aggregateType, long aggregateId, String commandId) {
        return jdbcTemplate.update("""
                UPDATE sys_async_command_state SET state = 'COMPLETED', update_time = NOW()
                WHERE owner_module = ? AND aggregate_type = ? AND aggregate_id = ?
                  AND command_id = ? AND state = 'PENDING'
                """, owner, aggregateType, aggregateId, commandId) == 1;
    }
}
