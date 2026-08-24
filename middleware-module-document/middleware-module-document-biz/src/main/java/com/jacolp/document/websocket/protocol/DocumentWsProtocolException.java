package com.jacolp.document.websocket.protocol;

/** 会返回给客户端的文档 WebSocket 协议违规异常。 */
public class DocumentWsProtocolException extends RuntimeException {

    public DocumentWsProtocolException(String message) {
        super(message);
    }

    public DocumentWsProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
