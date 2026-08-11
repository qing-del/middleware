package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.exception.AuthenticationException;

/** Uniform non-enumerating invalid_grant rejection for CORE AGENT authorization-code exchange. */
public final class CoreAgentAuthorizationCodeExchangeRejectedException extends AuthenticationException {

    public static final String MESSAGE = "CORE AGENT authorization code exchange rejected";

    public CoreAgentAuthorizationCodeExchangeRejectedException() {
        super(MESSAGE);
    }
}
