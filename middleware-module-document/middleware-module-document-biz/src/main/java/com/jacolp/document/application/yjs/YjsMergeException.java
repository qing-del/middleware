package com.jacolp.document.application.yjs;

/** Raised when the merge service cannot provide a valid merged Yjs state. */
public class YjsMergeException extends RuntimeException {

    public YjsMergeException(String message) {
        super(message);
    }

    public YjsMergeException(String message, Throwable cause) {
        super(message, cause);
    }
}
