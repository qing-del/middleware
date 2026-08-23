package com.jacolp.document.websocket;

/** Raised when a session tries to use a Room outside its authenticated personal scope. */
public class DocumentRoomAccessException extends RuntimeException {

    public DocumentRoomAccessException(String message) {
        super(message);
    }
}
