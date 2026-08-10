package com.jacolp.module.system.biz.application.port.out;

import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeState;

import java.util.Optional;

/** Read/replace/consume/delete port for protected email-code challenge state. */
public interface EmailLoginCodeStateStore {

    Optional<EmailLoginCodeState> find(String clientId, Long userId);

    void replace(EmailLoginCodeState state);

    boolean consume(String clientId, Long userId, String expectedVerifierHash);

    void delete(String clientId, Long userId);
}
