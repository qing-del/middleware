package com.jacolp.module.system.biz.web.authorization;

import com.jacolp.module.system.biz.application.authorization.model.CoreAgentBrowserAuthorizationTransaction;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpSessionCoreAgentAuthorizationTransactionStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-11T06:00:00Z");
    private static final String STATE = "browser-state";
    private static final String CHALLENGE = base64Url((byte) 4);

    @Test
    void replacesTheSingleExactAttributeAndSetsTheConfirmedSessionTimeout() {
        MutableClock clock = new MutableClock(NOW);
        HttpSessionCoreAgentAuthorizationTransactionStore store = new HttpSessionCoreAgentAuthorizationTransactionStore(clock);
        MockHttpSession session = new MockHttpSession();
        CoreAgentBrowserAuthorizationTransaction first = transaction(41L, STATE, NOW);
        CoreAgentBrowserAuthorizationTransaction replacement = transaction(42L, "replacement-state", NOW);

        store.replace(session, first);
        store.replace(session, replacement);

        assertThat(session.getAttribute(HttpSessionCoreAgentAuthorizationTransactionStore.ATTRIBUTE_NAME)).isNotNull();
        assertThat(session.getMaxInactiveInterval())
                .isEqualTo(HttpSessionCoreAgentAuthorizationTransactionStore.SESSION_TIMEOUT_SECONDS);
        assertThat(store.find(session, STATE, 41L)).isEmpty();
        assertThat(store.find(session, "replacement-state", 42L)).contains(replacement);
    }

    @Test
    void rejectsFutureAndExpiredTransactionsAndDoesNotCreateSessionState() {
        MutableClock clock = new MutableClock(NOW);
        HttpSessionCoreAgentAuthorizationTransactionStore store = new HttpSessionCoreAgentAuthorizationTransactionStore(clock);
        MockHttpSession session = new MockHttpSession();

        assertThatIllegalArgumentException().isThrownBy(() -> store.replace(session,
                transaction(42L, STATE, NOW.plusSeconds(1))));
        assertThatIllegalArgumentException().isThrownBy(() -> store.replace(session,
                transaction(42L, STATE, NOW.minus(Duration.ofMinutes(10)))));
        assertThat(session.getAttribute(HttpSessionCoreAgentAuthorizationTransactionStore.ATTRIBUTE_NAME)).isNull();
    }

    @Test
    void findAndConsumeAreBoundToStateAndUserAndConsumptionIsSingleUse() {
        MutableClock clock = new MutableClock(NOW);
        HttpSessionCoreAgentAuthorizationTransactionStore store = new HttpSessionCoreAgentAuthorizationTransactionStore(clock);
        MockHttpSession session = new MockHttpSession();
        CoreAgentBrowserAuthorizationTransaction transaction = transaction(42L, STATE, NOW);
        store.replace(session, transaction);

        assertThat(store.find(session, "wrong", 42L)).isEmpty();
        assertThat(store.find(session, STATE, 41L)).isEmpty();
        assertThat(session.getAttribute(HttpSessionCoreAgentAuthorizationTransactionStore.ATTRIBUTE_NAME)).isNotNull();
        assertThat(store.consume(session, STATE, 42L)).contains(transaction);
        assertThat(store.consume(session, STATE, 42L)).isEmpty();
    }

    @Test
    void concurrentConsumesAgainstOneSessionAllowExactlyOneWinner() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        HttpSessionCoreAgentAuthorizationTransactionStore store = new HttpSessionCoreAgentAuthorizationTransactionStore(clock);
        MockHttpSession session = new MockHttpSession();
        store.replace(session, transaction(42L, STATE, NOW));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> consumeAfterStart(store, session, start));
            Future<Boolean> second = executor.submit(() -> consumeAfterStart(store, session, start));
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expirationOrChangedSessionIdRemovesTheTransactionWithoutLeakingIt() {
        MutableClock clock = new MutableClock(NOW);
        HttpSessionCoreAgentAuthorizationTransactionStore store = new HttpSessionCoreAgentAuthorizationTransactionStore(clock);
        MockHttpSession expiredSession = new MockHttpSession();
        store.replace(expiredSession, transaction(42L, STATE, NOW));
        clock.set(NOW.plus(Duration.ofMinutes(10)));

        assertThat(store.find(expiredSession, STATE, 42L)).isEmpty();
        assertThat(expiredSession.getAttribute(HttpSessionCoreAgentAuthorizationTransactionStore.ATTRIBUTE_NAME)).isNull();

        clock.set(NOW);
        AtomicReference<String> id = new AtomicReference<>("first-session");
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        HttpSession changingSession = session(id, attributes);
        store.replace(changingSession, transaction(42L, STATE, NOW));
        id.set("rotated-session");

        assertThat(store.find(changingSession, STATE, 42L)).isEmpty();
        assertThat(attributes).doesNotContainKey(HttpSessionCoreAgentAuthorizationTransactionStore.ATTRIBUTE_NAME);
    }

    @Test
    void pollutedAttributesFailClosedAndAreRemovedWithoutInvalidatingTheSession() {
        MutableClock clock = new MutableClock(NOW);
        HttpSessionCoreAgentAuthorizationTransactionStore store = new HttpSessionCoreAgentAuthorizationTransactionStore(clock);
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn("session-id");
        when(session.getAttribute(HttpSessionCoreAgentAuthorizationTransactionStore.ATTRIBUTE_NAME)).thenReturn("polluted");

        assertThatIllegalStateException().isThrownBy(() -> store.find(session, STATE, 42L));
        verify(session).removeAttribute(HttpSessionCoreAgentAuthorizationTransactionStore.ATTRIBUTE_NAME);
        verify(session, never()).invalidate();
    }

    @Test
    void nullSessionsFailClosedAndInvalidateOnlyRemovesTheTransaction() {
        HttpSessionCoreAgentAuthorizationTransactionStore store = new HttpSessionCoreAgentAuthorizationTransactionStore(
                Clock.fixed(NOW, ZoneOffset.UTC));
        MockHttpSession session = new MockHttpSession();
        store.replace(session, transaction(42L, STATE, NOW));

        assertThat(store.find(null, STATE, 42L)).isEmpty();
        assertThat(store.consume(null, STATE, 42L)).isEmpty();
        store.invalidate(null);
        store.invalidate(session);
        assertThat(session.getAttribute(HttpSessionCoreAgentAuthorizationTransactionStore.ATTRIBUTE_NAME)).isNull();
    }

    private static boolean consumeAfterStart(HttpSessionCoreAgentAuthorizationTransactionStore store, HttpSession session,
                                             CountDownLatch start) throws InterruptedException {
        start.await();
        return store.consume(session, STATE, 42L).isPresent();
    }

    private static HttpSession session(AtomicReference<String> id, Map<String, Object> attributes) {
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenAnswer(invocation -> id.get());
        when(session.getAttribute(HttpSessionCoreAgentAuthorizationTransactionStore.ATTRIBUTE_NAME))
                .thenAnswer(invocation -> attributes.get(HttpSessionCoreAgentAuthorizationTransactionStore.ATTRIBUTE_NAME));
        doAnswer(invocation -> {
            attributes.put((String) invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(session).setAttribute(eq(HttpSessionCoreAgentAuthorizationTransactionStore.ATTRIBUTE_NAME),
                org.mockito.ArgumentMatchers.any());
        doAnswer(invocation -> {
            attributes.remove((String) invocation.getArgument(0));
            return null;
        }).when(session).removeAttribute(HttpSessionCoreAgentAuthorizationTransactionStore.ATTRIBUTE_NAME);
        return session;
    }

    private static CoreAgentBrowserAuthorizationTransaction transaction(long userId, String state, Instant issuedAt) {
        return new CoreAgentBrowserAuthorizationTransaction("core_agent", "http://127.0.0.1:9090/oauth/callback",
                List.of("note:read"), CHALLENGE, "S256", state, "192.0.2.25", userId, issuedAt,
                issuedAt.plus(Duration.ofMinutes(10)));
    }

    private static String base64Url(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
