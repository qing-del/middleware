package com.jacolp.document.websocket.protocol;

/** The v0.3 text-frame control protocol. */
public enum DocumentWsControlType {
    JOIN_DOCUMENT,
    JOIN_ACCEPTED,
    SYNC_COMPLETE,
    LEAVE_DOCUMENT,
    UPDATE_ACCEPTED,
    AWARENESS_META,
    ERROR,
    PING,
    PONG
}
