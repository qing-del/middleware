package com.jacolp.document.websocket.protocol;

import java.util.UUID;

/** JSON control frame. Fields that do not apply to a control type are null. */
public record DocumentWsControlMessage(
        int protocolVersion,
        DocumentWsControlType type,
        UUID requestId,
        Long documentId,
        UUID clientUpdateId,
        String redisOpId,
        String code,
        String message) {
}
