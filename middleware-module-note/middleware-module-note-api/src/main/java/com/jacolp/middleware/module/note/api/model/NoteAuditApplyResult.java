package com.jacolp.middleware.module.note.api.model;

/**
 * Counts the note rows and note-to-note relation rows changed by one audit decision.
 */
public record NoteAuditApplyResult(int noteRowsUpdated, int relationRowsUpdated) {
}
