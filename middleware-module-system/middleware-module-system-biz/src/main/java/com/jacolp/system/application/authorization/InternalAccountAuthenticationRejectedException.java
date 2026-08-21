package com.jacolp.system.application.authorization;

import com.jacolp.exception.AuthenticationException;

/**
 * Deliberately uniform rejection for ordinary internal account authentication failures.
 */
public final class InternalAccountAuthenticationRejectedException extends AuthenticationException {

    public static final String MESSAGE = "Internal account authentication rejected";

    public InternalAccountAuthenticationRejectedException() {
        super(MESSAGE);
    }
}
