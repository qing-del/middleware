package com.jacolp.document.application.yjs;

import java.util.List;

/** Delegates Yjs apply/encode work to the isolated TypeScript merge service. */
public interface YjsMergeClient {

    /**
     * Applies the optional base state and ordered updates using the official Yjs implementation.
     * Java treats every value as opaque binary and never parses Yjs content.
     */
    byte[] merge(byte[] baseState, List<byte[]> updates);
}
