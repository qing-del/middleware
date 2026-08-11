package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.exception.AuthenticationException;

/** Uniform non-enumerating rejection for the CORE AGENT browser login page. */
public final class CoreAgentBrowserAuthenticationRejectedException extends AuthenticationException {

    public static final String MESSAGE = "CORE AGENT browser authentication rejected";

    public CoreAgentBrowserAuthenticationRejectedException() {
        super(MESSAGE);
    }
}
