package com.jacolp.document.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.document.application.close.DocumentRoomLifecycleService;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.redis.DocumentPendingUpdate;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.messaging.DocumentSchedulePublisher;
import com.jacolp.document.websocket.protocol.DocumentWsBinaryFrame;
import com.jacolp.document.websocket.protocol.DocumentWsCodec;
import com.jacolp.document.websocket.protocol.DocumentWsControlMessage;
import com.jacolp.document.websocket.protocol.DocumentWsControlType;
import com.jacolp.document.websocket.protocol.DocumentWsFrameType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

class DocumentWebSocketHandlerTest {

    @Test
    void joinsPersonalDocumentThenAcknowledgesOnlyAfterRedisAcceptsTheUpdate() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-a", principal(42L));
        DocumentDO document = document(7L, 42L);

        when(documentMapper.selectActiveByIdAndTeamId(7L, 42L)).thenReturn(document);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        when(redisRepository.appendPendingUpdate(any(DocumentPendingUpdate.class))).thenReturn("123-0");
        when(documentMapper.updateLastModificationIfActive(eq(7L), eq(42L), any(LocalDateTime.class), eq(42L))).thenReturn(1);

        DocumentWebSocketHandler handler = new DocumentWebSocketHandler(codec, documentMapper, redisRepository,
                new DocumentRoomManager(properties), bootstrapService, schedulePublisher, presenceRegistry,
                lifecycleService, properties);
        UUID joinRequestId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, joinRequestId, 7L, null, null, null, null)));

        ArgumentCaptor<WebSocketMessage<?>> joinMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(2)).sendMessage(joinMessages.capture());
        assertThat(codec.decodeControl((TextMessage) joinMessages.getAllValues().get(0)).type())
                .isEqualTo(DocumentWsControlType.JOIN_ACCEPTED);
        assertThat(codec.decodeControl((TextMessage) joinMessages.getAllValues().get(1)).type())
                .isEqualTo(DocumentWsControlType.SYNC_COMPLETE);
        verify(bootstrapService).sendBootstrap(eq(7L), eq(42L), any(WebSocketSession.class));
        verify(presenceRegistry).register(7L, "session-a");
        verify(lifecycleService).reopen(document, 42L);

        UUID updateId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
        byte[] update = new byte[] {0, -1, 1};
        handler.handleMessage(session, codec.encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.CLIENT_UPDATE, updateId, update)));

        ArgumentCaptor<DocumentPendingUpdate> pendingUpdate = ArgumentCaptor.forClass(DocumentPendingUpdate.class);
        verify(redisRepository).appendPendingUpdate(pendingUpdate.capture());
        assertThat(pendingUpdate.getValue().documentId()).isEqualTo(7L);
        assertThat(pendingUpdate.getValue().clientUpdateId()).isEqualTo(updateId.toString());
        assertThat(pendingUpdate.getValue().updateData()).containsExactly(update);

        ArgumentCaptor<WebSocketMessage<?>> updateAck = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(3)).sendMessage(updateAck.capture());
        DocumentWsControlMessage ack = codec.decodeControl((TextMessage) updateAck.getAllValues().getLast());
        assertThat(ack.type()).isEqualTo(DocumentWsControlType.UPDATE_ACCEPTED);
        assertThat(ack.requestId()).isEqualTo(updateId);
        assertThat(ack.clientUpdateId()).isEqualTo(updateId);
        assertThat(ack.redisOpId()).isEqualTo("123-0");
        verify(schedulePublisher).scheduleFlushLog(7L);
    }

    @Test
    void rejectsJoiningAnotherDocumentOnTheSameWebSocketSession() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-b", principal(42L));
        when(documentMapper.selectActiveByIdAndTeamId(7L, 42L)).thenReturn(document(7L, 42L));
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        DocumentWebSocketHandler handler = new DocumentWebSocketHandler(codec, documentMapper, redisRepository,
                new DocumentRoomManager(properties), bootstrapService, schedulePublisher, presenceRegistry,
                lifecycleService, properties);

        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null)));
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 8L, null, null, null, null)));

        ArgumentCaptor<WebSocketMessage<?>> error = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(3)).sendMessage(error.capture());
        DocumentWsControlMessage response = codec.decodeControl((TextMessage) error.getAllValues().getLast());
        assertThat(response.type()).isEqualTo(DocumentWsControlType.ERROR);
        assertThat(response.code()).isEqualTo("DOCUMENT_FORBIDDEN");
    }

    private static WebSocketSession session(String sessionId, CurrentPrincipal principal) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of(DocumentWebSocketHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, principal));
        return session;
    }

    private static CurrentPrincipal principal(long userId) {
        return new CurrentPrincipal(userId, "alice", "user", "password", List.of("USER"), List.of("document:write"));
    }

    private static DocumentDO document(long id, long teamId) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentDO(id, teamId, "title", null, 0L, now, teamId, false, 0L, now, now);
    }
}
