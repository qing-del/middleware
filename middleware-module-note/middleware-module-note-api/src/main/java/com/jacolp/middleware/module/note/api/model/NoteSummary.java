package com.jacolp.middleware.module.note.api.model;

/**
 * Minimal note data permitted outside the note module for batch display and relation resolution.
 */
public record NoteSummary(
        Long id,
        Long userId,
        Long topicId,
        String title,
        NoteLifecycleStatus status
) {
}
