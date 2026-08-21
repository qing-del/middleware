package com.jacolp.system.application.authorization;

import com.jacolp.exception.AuthenticationException;

/** Uniform non-enumerating rejection for a USER/ADMIN refresh-token grant. */
public final class InternalRefreshTokenRejectedException extends AuthenticationException {

    public static final String MESSAGE = "Internal refresh token rejected";

    public InternalRefreshTokenRejectedException() {
        super(MESSAGE);
    }
}
