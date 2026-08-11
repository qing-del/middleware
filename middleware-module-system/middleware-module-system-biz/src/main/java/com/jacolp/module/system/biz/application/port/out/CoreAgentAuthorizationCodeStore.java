package com.jacolp.module.system.biz.application.port.out;

import com.jacolp.module.system.biz.application.authorization.model.CoreAgentAuthorizationCodeState;
import com.jacolp.module.system.biz.application.authorization.model.IssuedCoreAgentAuthorizationCode;

import java.util.Optional;

/**
 * Atomic persistence boundary for unexchanged CORE AGENT authorization codes.
 *
 * <p>{@link #replaceCurrent(CoreAgentAuthorizationCodeState)} replaces the current code for the
 * state account atomically: it creates the code entry and points that user at it while invalidating
 * a preceding entry. {@link #findByCode(String)} never consumes the code, so a failed PKCE verifier
 * or security-snapshot check leaves a valid code available for one retry. {@link #consume(String, Long)}
 * deletes both the code and its user pointer only when that pointer still names the supplied code;
 * two valid concurrent consumes therefore yield exactly one {@code true}. {@link #invalidateCurrent(Long)}
 * only removes the code currently named by the pointer, never a replacement issued concurrently.</p>
 *
 * <p>This port intentionally exposes no Redis, persistence data-object, HTTP, or SAS types.</p>
 */
public interface CoreAgentAuthorizationCodeStore {

    IssuedCoreAgentAuthorizationCode replaceCurrent(CoreAgentAuthorizationCodeState state);

    Optional<CoreAgentAuthorizationCodeState> findByCode(String rawCode);

    boolean consume(String rawCode, Long expectedUserId);

    void invalidateCurrent(Long userId);
}
