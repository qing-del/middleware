package com.jacolp.module.system.biz.web.authorization;

import com.jacolp.module.system.biz.application.authorization.model.CoreAgentBrowserAuthorizationTransaction;
import jakarta.servlet.http.HttpSession;

import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * One current CORE AGENT consent transaction per browser session.
 *
 * <p>All mutation and compare-and-consume operations synchronize on the supplied session. The
 * adapter never invalidates the whole session because the authorization response still needs its
 * Spring Security context; a later success handler owns complete browser-session cleanup.</p>
 */
public final class HttpSessionCoreAgentAuthorizationTransactionStore {

    static final String ATTRIBUTE_NAME = HttpSessionCoreAgentAuthorizationTransactionStore.class.getName()
            + ".CURRENT_TRANSACTION";
    static final int SESSION_TIMEOUT_SECONDS = 600;

    private final Clock clock;

    public HttpSessionCoreAgentAuthorizationTransactionStore(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock is required");
        }
        this.clock = clock;
    }

    /** Atomically replaces the current transaction without extending any existing expiry on reads. */
    public void replace(HttpSession session, CoreAgentBrowserAuthorizationTransaction transaction) {
        if (session == null || transaction == null) {
            throw new IllegalArgumentException("session and transaction are required");
        }
        synchronized (session) {
            String sessionId = requireSessionId(session);
            Instant now = clock.instant();
            if (transaction.issuedAt().isAfter(now) || !now.isBefore(transaction.expiresAt())) {
                throw new IllegalArgumentException("transaction must be current when stored");
            }
            session.setAttribute(ATTRIBUTE_NAME, new SessionBoundTransaction(sessionId, transaction));
            session.setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);
        }
    }

    /** Finds a current transaction only when both the OAuth state and authenticated user match. */
    public Optional<CoreAgentBrowserAuthorizationTransaction> find(HttpSession session, String expectedState,
                                                                     long expectedUserId) {
        if (session == null) {
            return Optional.empty();
        }
        synchronized (session) {
            return current(session, expectedState, expectedUserId);
        }
    }

    /**
     * Atomically returns and removes a matching current transaction. Two matching concurrent calls
     * against the same session cannot both consume it.
     */
    public Optional<CoreAgentBrowserAuthorizationTransaction> consume(HttpSession session, String expectedState,
                                                                        long expectedUserId) {
        if (session == null) {
            return Optional.empty();
        }
        synchronized (session) {
            Optional<CoreAgentBrowserAuthorizationTransaction> transaction = current(session, expectedState,
                    expectedUserId);
            if (transaction.isPresent()) {
                session.removeAttribute(ATTRIBUTE_NAME);
            }
            return transaction;
        }
    }

    /** Removes the current transaction only; it never invalidates the broader browser session. */
    public void invalidate(HttpSession session) {
        if (session == null) {
            return;
        }
        synchronized (session) {
            Object attribute = session.getAttribute(ATTRIBUTE_NAME);
            if (attribute == null) {
                return;
            }
            if (!(attribute instanceof SessionBoundTransaction)) {
                session.removeAttribute(ATTRIBUTE_NAME);
                throw pollutedAttribute();
            }
            session.removeAttribute(ATTRIBUTE_NAME);
        }
    }

    private Optional<CoreAgentBrowserAuthorizationTransaction> current(HttpSession session, String expectedState,
                                                                         long expectedUserId) {
        Object attribute = session.getAttribute(ATTRIBUTE_NAME);
        if (attribute == null) {
            return Optional.empty();
        }
        if (!(attribute instanceof SessionBoundTransaction stored)) {
            session.removeAttribute(ATTRIBUTE_NAME);
            throw pollutedAttribute();
        }
        String currentSessionId = requireSessionId(session);
        CoreAgentBrowserAuthorizationTransaction transaction = stored.transaction();
        Instant now = clock.instant();
        if (!stored.sessionId().equals(currentSessionId) || transaction.issuedAt().isAfter(now)
                || !now.isBefore(transaction.expiresAt())) {
            session.removeAttribute(ATTRIBUTE_NAME);
            return Optional.empty();
        }
        if (expectedUserId <= 0 || !constantTimeEquals(transaction.oauthState(), expectedState)
                || transaction.authenticatedUserId() != expectedUserId) {
            return Optional.empty();
        }
        return Optional.of(transaction);
    }

    private static String requireSessionId(HttpSession session) {
        String sessionId = session.getId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("CORE AGENT authorization transaction session is invalid");
        }
        return sessionId;
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static IllegalStateException pollutedAttribute() {
        return new IllegalStateException("CORE AGENT authorization transaction session attribute is invalid");
    }

    private record SessionBoundTransaction(String sessionId, CoreAgentBrowserAuthorizationTransaction transaction)
            implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
