package com.jacolp.document.api.model;

/** Runtime-only lifecycle states for a collaborative document room. */
public enum DocumentRoomLifecycleState {
    OPEN,
    ACTIVE,
    PRE_CLOSE,
    CLOSING,
    CLOSED
}
