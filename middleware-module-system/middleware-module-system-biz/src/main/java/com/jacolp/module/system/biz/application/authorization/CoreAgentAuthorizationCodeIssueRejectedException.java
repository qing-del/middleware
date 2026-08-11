package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.exception.AuthenticationException;

/** Uniform non-enumerating rejection while issuing a CORE AGENT authorization code. */
public final class CoreAgentAuthorizationCodeIssueRejectedException extends AuthenticationException {

    public static final String MESSAGE = "CORE AGENT authorization code issuance rejected";

    public CoreAgentAuthorizationCodeIssueRejectedException() {
        super(MESSAGE);
    }
}
