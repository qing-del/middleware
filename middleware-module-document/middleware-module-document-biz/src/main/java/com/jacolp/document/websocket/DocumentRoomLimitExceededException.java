package com.jacolp.document.websocket;

/** Raised when a Room would exceed its configured concurrent-session bound. */
public class DocumentRoomLimitExceededException extends RuntimeException {

    public DocumentRoomLimitExceededException(String message) {
        super(message);
    }
}
