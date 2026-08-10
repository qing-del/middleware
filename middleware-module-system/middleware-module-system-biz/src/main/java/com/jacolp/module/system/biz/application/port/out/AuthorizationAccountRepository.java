package com.jacolp.module.system.biz.application.port.out;

import com.jacolp.module.system.biz.application.authorization.model.AuthorizationAccount;

import java.util.Optional;

/**
 * Read-only account lookup port for authorization flows.
 */
public interface AuthorizationAccountRepository {

    Optional<AuthorizationAccount> findById(Long userId);

    Optional<AuthorizationAccount> findByUsername(String username);

    Optional<AuthorizationAccount> findByEmail(String email);
}
