package com.jacolp.document.controller;

import com.jacolp.document.enums.DocumentPermission;
import java.util.Objects;

/** Successful document share-link redemption result. */
public record DocumentShareLinkRedeemResponse(long documentId, DocumentPermission permission, boolean owner) {
    public DocumentShareLinkRedeemResponse {
        if (documentId <= 0) throw new IllegalArgumentException("documentId must be positive");
        Objects.requireNonNull(permission, "permission must not be null");
    }
}
