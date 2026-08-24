package com.jacolp.document.websocket;

/** 会话尝试访问不属于其已认证个人空间的 Room 时抛出。 */
public class DocumentRoomAccessException extends RuntimeException {

    public DocumentRoomAccessException(String message) {
        super(message);
    }
}
