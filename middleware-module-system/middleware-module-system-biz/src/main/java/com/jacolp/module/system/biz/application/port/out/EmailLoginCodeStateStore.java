package com.jacolp.module.system.biz.application.port.out;

import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeState;

import java.util.Optional;

/** Read/replace/delete port for protected email-code challenge state. */
public interface EmailLoginCodeStateStore {

    Optional<EmailLoginCodeState> find(String clientId, Long userId);

    void replace(EmailLoginCodeState state);

    void delete(String clientId, Long userId);
}
