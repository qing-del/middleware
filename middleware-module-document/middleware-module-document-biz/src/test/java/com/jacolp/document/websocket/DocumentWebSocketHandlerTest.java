package com.jacolp.document.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.document.application.access.DocumentAccess;
import com.jacolp.document.application.access.DocumentAccessDeniedException;
import com.jacolp.document.application.access.DocumentAccessService;
import com.jacolp.document.application.close.DocumentRoomLifecycleService;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.redis.DocumentPendingUpdate;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.messaging.DocumentSchedulePublisher;
import com.jacolp.document.websocket.protocol.DocumentWsAwarenessAction;
import com.jacolp.document.websocket.protocol.DocumentWsAwarenessMeta;
import com.jacolp.document.websocket.protocol.DocumentWsBinaryFrame;
import com.jacolp.document.websocket.protocol.DocumentWsCodec;
import com.jacolp.document.websocket.protocol.DocumentWsControlMessage;
import com.jacolp.document.websocket.protocol.DocumentWsControlType;
import com.jacolp.document.websocket.protocol.DocumentWsFrameType;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

class DocumentWebSocketHandlerTest {

    @Test
    void ownerCanJoinAndSubmitUpdatesAfterWriteAclIsRechecked() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-a", principal(42L, "document:write"));
        DocumentDO document = document(7L, 42L);
        DocumentAccess access = access(document, DocumentPermission.WRITE, true);

        when(accessService.requireRead(7L, 42L)).thenReturn(access);
        when(accessService.requireWrite(7L, 42L)).thenReturn(access);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        when(redisRepository.appendPendingUpdate(any(DocumentPendingUpdate.class))).thenReturn("123-0");
        when(documentMapper.updateLastModificationIfActive(eq(7L), eq(42L), any(LocalDateTime.class), eq(42L)))
                .thenReturn(1);

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);
        UUID joinRequestId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, joinRequestId, 7L, null, null, null, null, 101L)));

        ArgumentCaptor<WebSocketMessage<?>> joinMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(2)).sendMessage(joinMessages.capture());
        assertThat(codec.decodeControl((TextMessage) joinMessages.getAllValues().get(0)).type())
                .isEqualTo(DocumentWsControlType.JOIN_ACCEPTED);
        assertThat(codec.decodeControl((TextMessage) joinMessages.getAllValues().get(1)).type())
                .isEqualTo(DocumentWsControlType.SYNC_COMPLETE);
        verify(bootstrapService).sendBootstrap(eq(7L), any(WebSocketSession.class));
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
        verify(accessService).requireWrite(7L, 42L);

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
    void readOnlyCollaboratorCanBootstrapButCannotSubmitClientUpdate() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-read", principal(43L, "document:read"));
        DocumentAccess access = access(document(7L, 42L), DocumentPermission.READ, false);
        when(accessService.requireRead(7L, 43L)).thenReturn(access);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 102L)));

        UUID updateId = UUID.randomUUID();
        handler.handleMessage(session, codec.encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.CLIENT_UPDATE, updateId, new byte[] {1, 2, 3})));

        ArgumentCaptor<WebSocketMessage<?>> messages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(3)).sendMessage(messages.capture());
        DocumentWsControlMessage error = codec.decodeControl((TextMessage) messages.getAllValues().getLast());
        assertThat(error.type()).isEqualTo(DocumentWsControlType.ERROR);
        assertThat(error.code()).isEqualTo("DOCUMENT_FORBIDDEN");
        verify(redisRepository, never()).appendPendingUpdate(any(DocumentPendingUpdate.class));
        verify(documentMapper, never()).updateLastModificationIfActive(anyLong(), anyLong(), any(), anyLong());
        verify(accessService, never()).requireWrite(anyLong(), anyLong());
    }

    @Test
    void writeCollaboratorCanJoinAndSubmitUpdatesInTheOwnerRoom() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-write", principal(43L, "document:write"));
        DocumentDO document = document(7L, 42L);
        DocumentAccess access = access(document, DocumentPermission.WRITE, false);
        when(accessService.requireRead(7L, 43L)).thenReturn(access);
        when(accessService.requireWrite(7L, 43L)).thenReturn(access);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        when(redisRepository.appendPendingUpdate(any(DocumentPendingUpdate.class))).thenReturn("124-0");
        when(documentMapper.updateLastModificationIfActive(eq(7L), eq(43L), any(LocalDateTime.class), eq(43L)))
                .thenReturn(1);

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 103L)));
        handler.handleMessage(session, codec.encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.CLIENT_UPDATE, UUID.randomUUID(), new byte[] {4, 5})));

        verify(redisRepository).appendPendingUpdate(any(DocumentPendingUpdate.class));
        verify(documentMapper).updateLastModificationIfActive(eq(7L), eq(43L), any(LocalDateTime.class), eq(43L));
        verify(schedulePublisher).scheduleFlushLog(7L);
    }

    @Test
    void writeAclWithoutGlobalWriteScopeCannotSubmitClientUpdate() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-read-scope", principal(43L, "document:read"));
        DocumentAccess access = access(document(7L, 42L), DocumentPermission.WRITE, false);
        when(accessService.requireRead(7L, 43L)).thenReturn(access);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 104L)));
        UUID updateId = UUID.randomUUID();
        handler.handleMessage(session, codec.encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.CLIENT_UPDATE, updateId, new byte[] {6, 7})));

        ArgumentCaptor<WebSocketMessage<?>> messages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(3)).sendMessage(messages.capture());
        DocumentWsControlMessage error = codec.decodeControl((TextMessage) messages.getAllValues().getLast());
        assertThat(error.type()).isEqualTo(DocumentWsControlType.ERROR);
        assertThat(error.code()).isEqualTo("DOCUMENT_FORBIDDEN");
        verify(accessService, never()).requireWrite(anyLong(), anyLong());
        verify(redisRepository, never()).appendPendingUpdate(any(DocumentPendingUpdate.class));
        verify(documentMapper, never()).updateLastModificationIfActive(anyLong(), anyLong(), any(), anyLong());
        verify(schedulePublisher, never()).scheduleFlushLog(anyLong());
    }

    @Test
    void rejectsUnauthorizedJoinWithoutCreatingRoomOrPresence() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-denied", principal(43L, "document:read"));
        when(accessService.requireRead(7L, 43L)).thenThrow(DocumentAccessDeniedException.forbidden());

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 105L)));

        ArgumentCaptor<WebSocketMessage<?>> message = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(message.capture());
        assertThat(codec.decodeControl((TextMessage) message.getValue()).code()).isEqualTo("DOCUMENT_FORBIDDEN");
        verify(bootstrapService, never()).sendBootstrap(anyLong(), any());
        verify(presenceRegistry, never()).register(anyLong(), any());
    }

    @Test
    void rejectsJoiningAnotherDocumentOnTheSameWebSocketSession() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-b", principal(42L, "document:write"));
        when(accessService.requireRead(7L, 42L)).thenReturn(access(document(7L, 42L), DocumentPermission.WRITE, true));
        when(accessService.requireRead(8L, 42L)).thenReturn(access(document(8L, 42L), DocumentPermission.WRITE, true));
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);

        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 106L)));
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 8L, null, null, null, null, 107L)));

        ArgumentCaptor<WebSocketMessage<?>> error = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(3)).sendMessage(error.capture());
        DocumentWsControlMessage response = codec.decodeControl((TextMessage) error.getAllValues().getLast());
        assertThat(response.type()).isEqualTo(DocumentWsControlType.ERROR);
        assertThat(response.code()).isEqualTo("DOCUMENT_FORBIDDEN");
    }

    @Test
    void bootstrapFailureReleasesTheSessionColor() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentRoomManager roomManager = new DocumentRoomManager(properties);
        WebSocketSession session = session("session-bootstrap", principal(42L, "document:write"));
        DocumentAccess access = access(document(7L, 42L), DocumentPermission.WRITE, true);
        when(accessService.requireRead(7L, 42L)).thenReturn(access);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new DocumentBootstrapException("bootstrap failed"))
                .when(bootstrapService).sendBootstrap(eq(7L), any(WebSocketSession.class));

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties, roomManager);
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 108L)));

        DocumentRoom room = roomManager.find(7L).orElseThrow();
        assertThat(room.sessionCount()).isZero();
        DocumentSessionContext replacement = room.join(session("session-bootstrap", principal(42L, "document:write")),
                principal(42L, "document:write"), access, 108L);
        assertThat(replacement.cursorColor()).matches("#[0-9A-F]{6}");
        verify(presenceRegistry, atLeastOnce()).unregister("session-bootstrap");
    }

    @Test
    void normalCloseAndTransportErrorReleaseSessionColors() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentRoomManager roomManager = new DocumentRoomManager(properties);
        DocumentDO document = document(7L, 42L);
        DocumentAccess access = access(document, DocumentPermission.WRITE, true);
        when(accessService.requireRead(7L, 42L)).thenReturn(access);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties, roomManager);

        WebSocketSession normalSession = session("session-normal-close", principal(42L, "document:write"));
        handler.handleMessage(normalSession, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 109L)));
        DocumentRoom room = roomManager.find(7L).orElseThrow();
        String normalColor = room.requireSession("session-normal-close").cursorColor();
        handler.afterConnectionClosed(normalSession, CloseStatus.NORMAL);
        assertThat(room.sessionCount()).isZero();
        assertThat(room.join(session("session-normal-close", principal(42L, "document:write")),
                principal(42L, "document:write"), access, 109L).cursorColor())
                .isEqualTo(normalColor);
        room.leave("session-normal-close");

        WebSocketSession transportSession = session("session-transport-error", principal(42L, "document:write"));
        handler.handleMessage(transportSession, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 110L)));
        String transportColor = room.requireSession("session-transport-error").cursorColor();
        handler.handleTransportError(transportSession, new IOException("transport failed"));
        assertThat(room.sessionCount()).isZero();
        assertThat(room.join(session("session-transport-error", principal(42L, "document:write")),
                principal(42L, "document:write"), access, 110L).cursorColor())
                .isEqualTo(transportColor);
    }

    @Test
    void rejectsMissingAndInvalidAwarenessClientIdBeforeCreatingRoom() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentRoomManager roomManager = new DocumentRoomManager(properties);
        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties, roomManager);

        WebSocketSession missing = session("session-awareness-missing", principal(42L, "document:write"));
        handler.handleMessage(missing, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null)));
        ArgumentCaptor<WebSocketMessage<?>> missingMessage = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(missing).sendMessage(missingMessage.capture());
        assertThat(codec.decodeControl((TextMessage) missingMessage.getValue()).code())
                .isEqualTo("DOCUMENT_AWARENESS_CLIENT_ID_REQUIRED");

        WebSocketSession invalid = session("session-awareness-invalid", principal(42L, "document:write"));
        handler.handleMessage(invalid, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 0L)));
        ArgumentCaptor<WebSocketMessage<?>> invalidMessage = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(invalid).sendMessage(invalidMessage.capture());
        assertThat(codec.decodeControl((TextMessage) invalidMessage.getValue()).code())
                .isEqualTo("DOCUMENT_AWARENESS_CLIENT_ID_INVALID");

        WebSocketSession negative = session("session-awareness-negative", principal(42L, "document:write"));
        handler.handleMessage(negative, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, -1L)));
        ArgumentCaptor<WebSocketMessage<?>> negativeMessage = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(negative).sendMessage(negativeMessage.capture());
        assertThat(codec.decodeControl((TextMessage) negativeMessage.getValue()).code())
                .isEqualTo("DOCUMENT_AWARENESS_CLIENT_ID_INVALID");

        assertThat(roomManager.find(7L)).isEmpty();
        verify(accessService, never()).requireRead(anyLong(), anyLong());
        verify(presenceRegistry, never()).register(anyLong(), any());
    }

    @Test
    void publishesAwarenessMetadataOnJoinAndRemoveOnLeave() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentRoomManager roomManager = new DocumentRoomManager(properties);
        DocumentDO document = document(7L, 42L);
        DocumentAccess ownerAccess = access(document, DocumentPermission.WRITE, true);
        DocumentAccess collaboratorAccess = access(document, DocumentPermission.READ, false);
        when(accessService.requireRead(7L, 42L)).thenReturn(ownerAccess);
        when(accessService.requireRead(7L, 43L)).thenReturn(collaboratorAccess);

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties, roomManager);
        WebSocketSession owner = session("session-awareness-owner", principal(42L, "document:write"));
        WebSocketSession collaborator = session("session-awareness-collaborator", principal(43L, "document:read"));

        handler.handleMessage(owner, codec.encodeControl(joinControl(7L, 2001L)));
        handler.handleMessage(collaborator, codec.encodeControl(joinControl(7L, 2002L)));

        ArgumentCaptor<WebSocketMessage<?>> ownerMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(owner, times(3)).sendMessage(ownerMessages.capture());
        DocumentWsAwarenessMeta ownerUpsert = codec.decodeAwarenessMeta(
                (TextMessage) ownerMessages.getAllValues().get(2));
        assertThat(ownerUpsert.action()).isEqualTo(DocumentWsAwarenessAction.UPSERT);
        assertThat(ownerUpsert.documentId()).isEqualTo(7L);
        assertThat(ownerUpsert.awarenessClientId()).isEqualTo(2002L);
        assertThat(ownerUpsert.sessionId()).isEqualTo("session-awareness-collaborator");
        assertThat(ownerUpsert.userId()).isEqualTo(43L);
        assertThat(ownerUpsert.name()).isEqualTo("alice");
        assertThat(ownerUpsert.color()).matches("#[0-9A-F]{6}");

        ArgumentCaptor<WebSocketMessage<?>> collaboratorMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(collaborator, times(3)).sendMessage(collaboratorMessages.capture());
        DocumentWsAwarenessMeta collaboratorUpsert = codec.decodeAwarenessMeta(
                (TextMessage) collaboratorMessages.getAllValues().get(1));
        assertThat(collaboratorUpsert.action()).isEqualTo(DocumentWsAwarenessAction.UPSERT);
        assertThat(collaboratorUpsert.awarenessClientId()).isEqualTo(2001L);
        assertThat(collaboratorUpsert.sessionId()).isEqualTo("session-awareness-owner");
        assertThat(collaboratorUpsert.userId()).isEqualTo(42L);

        handler.handleMessage(owner, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.LEAVE_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null)));
        ArgumentCaptor<WebSocketMessage<?>> collaboratorAfterLeave = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(collaborator, times(4)).sendMessage(collaboratorAfterLeave.capture());
        DocumentWsAwarenessMeta remove = codec.decodeAwarenessMeta(
                (TextMessage) collaboratorAfterLeave.getAllValues().get(3));
        assertThat(remove.action()).isEqualTo(DocumentWsAwarenessAction.REMOVE);
        assertThat(remove.documentId()).isEqualTo(7L);
        assertThat(remove.awarenessClientId()).isEqualTo(2001L);
        assertThat(remove.sessionId()).isEqualTo("session-awareness-owner");
        assertThat(remove.userId()).isNull();
        assertThat(remove.name()).isNull();
        assertThat(remove.color()).isNull();

        handler.afterConnectionClosed(owner, CloseStatus.NORMAL);
        verify(collaborator, times(4)).sendMessage(any(WebSocketMessage.class));
        assertThat(roomManager.find(7L).orElseThrow().sessionCount()).isEqualTo(1);
        verify(presenceRegistry).unregister("session-awareness-owner");
    }

    @Test
    void removesFailedBroadcastSessionAndPublishesItsAwarenessRemove() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentRoomManager roomManager = new DocumentRoomManager(properties);
        DocumentDO document = document(7L, 42L);
        DocumentAccess ownerAccess = access(document, DocumentPermission.WRITE, true);
        DocumentAccess collaboratorAccess = access(document, DocumentPermission.READ, false);
        when(accessService.requireRead(7L, 42L)).thenReturn(ownerAccess);
        when(accessService.requireRead(7L, 43L)).thenReturn(collaboratorAccess);

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties, roomManager);
        WebSocketSession owner = session("session-awareness-failed", principal(42L, "document:write"));
        WebSocketSession collaborator = session("session-awareness-survivor", principal(43L, "document:read"));
        handler.handleMessage(owner, codec.encodeControl(joinControl(7L, 2101L)));
        doThrow(new IOException("send failed")).when(owner).sendMessage(any(WebSocketMessage.class));

        handler.handleMessage(collaborator, codec.encodeControl(joinControl(7L, 2102L)));

        ArgumentCaptor<WebSocketMessage<?>> collaboratorMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(collaborator, times(4)).sendMessage(collaboratorMessages.capture());
        DocumentWsAwarenessMeta remove = codec.decodeAwarenessMeta(
                (TextMessage) collaboratorMessages.getAllValues().get(2));
        assertThat(remove.action()).isEqualTo(DocumentWsAwarenessAction.REMOVE);
        assertThat(remove.awarenessClientId()).isEqualTo(2101L);
        assertThat(remove.sessionId()).isEqualTo("session-awareness-failed");
        assertThat(roomManager.find(7L).orElseThrow().sessionCount()).isEqualTo(1);
        verify(presenceRegistry).unregister("session-awareness-failed");
    }

    private static DocumentWebSocketHandler handler(DocumentWsCodec codec, DocumentMapper documentMapper,
                                                    DocumentAccessService accessService,
                                                    DocumentRedisRepository redisRepository,
                                                    DocumentBootstrapService bootstrapService,
                                                    DocumentSchedulePublisher schedulePublisher,
                                                    DocumentSessionPresenceRegistry presenceRegistry,
                                                    DocumentRoomLifecycleService lifecycleService,
                                                    DocumentProperties properties) {
        return handler(codec, documentMapper, accessService, redisRepository, bootstrapService, schedulePublisher,
                presenceRegistry, lifecycleService, properties, new DocumentRoomManager(properties));
    }

    private static DocumentWebSocketHandler handler(DocumentWsCodec codec, DocumentMapper documentMapper,
                                                    DocumentAccessService accessService,
                                                    DocumentRedisRepository redisRepository,
                                                    DocumentBootstrapService bootstrapService,
                                                    DocumentSchedulePublisher schedulePublisher,
                                                    DocumentSessionPresenceRegistry presenceRegistry,
                                                    DocumentRoomLifecycleService lifecycleService,
                                                    DocumentProperties properties,
                                                    DocumentRoomManager roomManager) {
        return new DocumentWebSocketHandler(codec, documentMapper, accessService, redisRepository,
                roomManager, bootstrapService, schedulePublisher, presenceRegistry,
                lifecycleService, properties);
    }

    private static WebSocketSession session(String sessionId, CurrentPrincipal principal) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of(DocumentWebSocketHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, principal));
        return session;
    }

    private static CurrentPrincipal principal(long userId, String scope) {
        return new CurrentPrincipal(userId, "alice", "user", "password", List.of("USER"), List.of(scope));
    }

    private static DocumentWsControlMessage joinControl(long documentId, long awarenessClientId) {
        return new DocumentWsControlMessage(1, DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), documentId,
                null, null, null, null, awarenessClientId);
    }

    private static DocumentDO document(long id, long ownerUserId) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentDO(id, ownerUserId, "title", null, 0L, now, ownerUserId, false, 0L, now, now);
    }

    private static DocumentAccess access(DocumentDO document, DocumentPermission permission, boolean owner) {
        return new DocumentAccess(document, permission, owner);
    }
}
