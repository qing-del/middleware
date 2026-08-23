package com.jacolp.document.api.model;

/** Scheduler signals that never carry document content or Yjs updates. */
public enum DocumentScheduleType {
    FLUSH_LOG,
    COMPACT,
    CLOSE
}
