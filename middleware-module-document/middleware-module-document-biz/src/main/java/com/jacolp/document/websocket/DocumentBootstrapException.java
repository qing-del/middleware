package com.jacolp.document.websocket;

/** 无法完整读取或发送文档 bootstrap 数据时抛出。 */
public class DocumentBootstrapException extends RuntimeException {

    public DocumentBootstrapException(String message, Throwable cause) {
        super(message, cause);
    }

    public DocumentBootstrapException(String message) {
        super(message);
    }
}
