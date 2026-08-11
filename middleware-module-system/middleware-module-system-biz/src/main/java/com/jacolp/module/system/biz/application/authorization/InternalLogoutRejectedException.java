package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.exception.AuthenticationException;

/** Stable, non-sensitive rejection when no eligible internal access token is present. */
public final class InternalLogoutRejectedException extends AuthenticationException {
    public InternalLogoutRejectedException() {
        super("Internal logout rejected");
    }
}
