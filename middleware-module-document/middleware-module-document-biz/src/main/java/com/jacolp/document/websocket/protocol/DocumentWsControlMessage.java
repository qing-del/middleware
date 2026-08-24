package com.jacolp.document.websocket.protocol;

import java.util.UUID;

/** JSON 控制帧；某个控制类型不需要的字段保持为 null。 */
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
