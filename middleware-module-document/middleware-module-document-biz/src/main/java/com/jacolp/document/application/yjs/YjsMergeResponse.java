package com.jacolp.document.application.yjs;

import java.util.Base64;

/** {@code POST /internal/yjs/merge} 返回的 JSON 响应报文。 */
record YjsMergeResponse(String mergedState) {

    byte[] decodeMergedState() {
        if (mergedState == null || mergedState.isBlank()) {
            throw new YjsMergeException("Yjs merge response is missing mergedState");
        }
        try {
            return Base64.getDecoder().decode(mergedState);
        } catch (IllegalArgumentException exception) {
            throw new YjsMergeException("Yjs merge response contains invalid Base64", exception);
        }
    }
}
