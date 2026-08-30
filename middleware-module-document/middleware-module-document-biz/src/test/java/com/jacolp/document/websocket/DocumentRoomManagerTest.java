package com.jacolp.document.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.document.application.access.DocumentAccess;
import com.jacolp.document.api.model.DocumentRoomLifecycleState;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class DocumentRoomManagerTest {

    @Test
    void keepsSessionsInSyncingUntilBootstrapCompletesAndStartsPreCloseWhenLastLeaves() {
        DocumentRoomManager manager = new DocumentRoomManager(properties(2));
        DocumentRoom room = manager.getOrCreate(7L, 42L);
        WebSocketSession session = session("session-a");

        DocumentSessionContext context = room.join(session, principal(42L), access(7L, 42L,
                DocumentPermission.WRITE, true));

        assertThat(context.syncStatus()).isEqualTo(DocumentSessionSyncStatus.SYNCING);
        assertThat(room.lifecycleState()).isEqualTo(DocumentRoomLifecycleState.ACTIVE);
        room.markActive("session-a");
        assertThat(context.syncStatus()).isEqualTo(DocumentSessionSyncStatus.ACTIVE);

        assertThat(room.leave("session-a")).isTrue();
        assertThat(room.sessionCount()).isZero();
        assertThat(room.lifecycleState()).isEqualTo(DocumentRoomLifecycleState.PRE_CLOSE);
        assertThat(manager.removeIfEmpty(7L)).isTrue();
        assertThat(manager.find(7L)).isEmpty();
        assertThat(manager.removeIfEmpty(7L)).isFalse();
    }

    @Test
    void allowsDifferentAuthorizedUsersAndEnforcesConfiguredRoomSessionLimit() {
        DocumentRoomManager manager = new DocumentRoomManager(properties(1));
        DocumentRoom room = manager.getOrCreate(8L, 42L);

        room.join(session("wrong-scope"), principal(43L), access(8L, 42L,
                DocumentPermission.READ, false));

        // The first read-only session consumes the configured slot.
        assertThatThrownBy(() -> room.join(session("second"), principal(42L), access(8L, 42L,
                DocumentPermission.WRITE, true)))
                .isInstanceOf(DocumentRoomLimitExceededException.class);
        assertThatThrownBy(() -> manager.getOrCreate(8L, 43L))
                .isInstanceOf(DocumentRoomAccessException.class);
    }

    @Test
    void closingRoomAcceptsNewJoinAndReturnsToActiveForReopenRace() {
        DocumentRoom room = new DocumentRoomManager(properties(2)).getOrCreate(9L, 42L);

        assertThat(room.beginClosingIfEmpty()).isTrue();
        room.join(session("returning"), principal(42L), access(9L, 42L,
                DocumentPermission.WRITE, true));

        assertThat(room.lifecycleState()).isEqualTo(DocumentRoomLifecycleState.ACTIVE);
        assertThat(room.sessionCount()).isEqualTo(1);
    }

    @Test
    void forwardsRemoteUpdatesToSessionsThatAreStillSynchronizing() throws Exception {
        DocumentRoom room = new DocumentRoomManager(properties(2)).getOrCreate(10L, 42L);
        WebSocketSession synchronizingSession = session("syncing");
        WebSocketSession activeSession = session("active");

        room.join(synchronizingSession, principal(42L), access(10L, 42L,
                DocumentPermission.READ, false));
        room.join(activeSession, principal(43L), access(10L, 42L,
                DocumentPermission.WRITE, false));
        room.markActive("active");

        WebSocketMessage<?> update = new BinaryMessage(new byte[] {1, 2, 3});
        room.broadcast(update, "active");

        verify(synchronizingSession).sendMessage(update);
    }

    private static DocumentProperties properties(int maxRoomSessions) {
        DocumentProperties properties = new DocumentProperties();
        properties.getWebsocket().setMaxRoomSessions(maxRoomSessions);
        properties.getWebsocket().setMaxSendQueueBytes(1024);
        return properties;
    }

    private static CurrentPrincipal principal(long userId) {
        return new CurrentPrincipal(userId, "user", "user", "password", List.of("USER"), List.of("document:write"));
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static DocumentAccess access(long documentId, long ownerUserId, DocumentPermission permission,
                                         boolean owner) {
        LocalDateTime now = LocalDateTime.now();
        DocumentDO document = new DocumentDO(documentId, ownerUserId, "title", null, 0L, now,
                ownerUserId, false, 0L, now, now);
        return new DocumentAccess(document, permission, owner);
    }
}
