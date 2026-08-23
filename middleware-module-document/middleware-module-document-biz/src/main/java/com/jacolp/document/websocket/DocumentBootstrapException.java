package com.jacolp.document.websocket;

/** The document bootstrap source could not be read or sent completely. */
public class DocumentBootstrapException extends RuntimeException {

    public DocumentBootstrapException(String message, Throwable cause) {
        super(message, cause);
    }

    public DocumentBootstrapException(String message) {
        super(message);
    }
}
