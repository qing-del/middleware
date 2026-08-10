package com.jacolp.module.system.biz.application.authorization;

import org.springframework.security.core.AuthenticationException;

/** Stable, non-enumerating rejection for ordinary email-code issuance denials. */
public class EmailLoginCodeIssuanceRejectedException extends AuthenticationException {

    public EmailLoginCodeIssuanceRejectedException() {
        super("Email-code issuance rejected");
    }
}
