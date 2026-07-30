package com.jacolp.middleware.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsyncCommandStateServiceTest {

    @Test
    void rejectsASecondPendingCommandForTheSameOwnedAggregate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenReturn(0)
                .thenThrow(new DuplicateKeyException("pending"));

        assertThat(new AsyncCommandStateService(jdbc)
                .tryBegin("NOTE", "TAG", 7L, "command-2", "CANCEL_AUDIT")).isFalse();
    }

    @Test
    void completesOnlyTheCurrentlyCorrelatedCommand() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertThat(new AsyncCommandStateService(jdbc)
                .completeIfCurrent("MEDIA", "IMAGE", 9L, "command-1")).isTrue();
    }
}
