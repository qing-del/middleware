package com.jacolp.document.application.yjs;

import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** JSON wire request for {@code POST /internal/yjs/merge}. */
record YjsMergeRequest(String baseState, List<String> updates) {

    YjsMergeRequest {
        updates = List.copyOf(Objects.requireNonNull(updates, "updates must not be null"));
    }

    static YjsMergeRequest from(byte[] baseState, List<byte[]> updates) {
        Objects.requireNonNull(updates, "updates must not be null");
        List<String> encodedUpdates = updates.stream()
                .map(update -> Base64.getEncoder().encodeToString(requireNonEmpty(update, "update")))
                .toList();
        return new YjsMergeRequest(
                baseState == null ? null : Base64.getEncoder().encodeToString(requireNonEmpty(baseState, "baseState")),
                encodedUpdates);
    }

    private static byte[] requireNonEmpty(byte[] data, String fieldName) {
        Objects.requireNonNull(data, fieldName + " must not be null");
        if (data.length == 0) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return data;
    }
}
