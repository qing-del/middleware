package com.jacolp.system.application.authorization;

import com.jacolp.exception.AuthenticationException;

/** Stable, non-enumerating rejection for ordinary email-code issuance denials. */
public class EmailLoginCodeIssuanceRejectedException extends AuthenticationException {

    public EmailLoginCodeIssuanceRejectedException() {
        super("Email-code issuance rejected");
    }
}
