package com.jacolp.document.websocket;

/** A joining session receives bootstrap and live frames while SYNCING, then becomes ACTIVE. */
public enum DocumentSessionSyncStatus {
    SYNCING,
    ACTIVE
}
