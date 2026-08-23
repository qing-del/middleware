package com.jacolp.document.websocket.protocol;

/** Client-visible document WebSocket protocol violation. */
public class DocumentWsProtocolException extends RuntimeException {

    public DocumentWsProtocolException(String message) {
        super(message);
    }

    public DocumentWsProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
