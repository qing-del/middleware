package com.jacolp.module.system.biz.application.port.out;

import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeState;
import java.util.Optional;

/** Read/delete port for protected email-code challenge state. */
public interface EmailLoginCodeStateStore {
    Optional<EmailLoginCodeState> find(String clientId, Long userId);
    void delete(String clientId, Long userId);
}
