package com.jacolp.document.websocket;

/** Room 将超过配置的并发会话上限时抛出。 */
public class DocumentRoomLimitExceededException extends RuntimeException {

    public DocumentRoomLimitExceededException(String message) {
        super(message);
    }
}
