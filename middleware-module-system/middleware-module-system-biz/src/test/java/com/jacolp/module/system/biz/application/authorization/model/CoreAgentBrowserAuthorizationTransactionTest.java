package com.jacolp.module.system.biz.application.authorization.model;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreAgentBrowserAuthorizationTransactionTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-11T06:00:00Z");
    private static final String CHALLENGE = base64Url((byte) 3);
    private static final String OAUTH_STATE = "opaque-browser-state";
    private static final String SOCKET_IP = "2001:db8::12";

    @Test
    void preservesNullRequestedScopesAndDefensivelyNormalizesNonNullScopes() {
        CoreAgentBrowserAuthorizationTransaction unspecified = transaction(null);
        CoreAgentBrowserAuthorizationTransaction specified = transaction(List.of("sys:read", "note:read"));

        assertThat(unspecified.requestedScopes()).isNull();
        assertThat(specified.requestedScopes()).containsExactly("note:read", "sys:read");
        assertThatThrownBy(() -> specified.requestedScopes().add("media:read"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validatesCoreAgentBindingPkceSocketStateUserScopesAndExactTtl() {
        assertThatIllegalArgumentException().isThrownBy(() -> value("user", redirectUri(), null, CHALLENGE, "S256",
                OAUTH_STATE, SOCKET_IP, 42L, expiresAt()));
        assertThatIllegalArgumentException().isThrownBy(() -> value("core_agent", "https://user@host.test/callback",
                null, CHALLENGE, "S256", OAUTH_STATE, SOCKET_IP, 42L, expiresAt()));
        assertThatIllegalArgumentException().isThrownBy(() -> transaction(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> transaction(List.of("note:read", "note:read")));
        assertThatIllegalArgumentException().isThrownBy(() -> value("core_agent", redirectUri(), null, "bad", "S256",
                OAUTH_STATE, SOCKET_IP, 42L, expiresAt()));
        assertThatIllegalArgumentException().isThrownBy(() -> value("core_agent", redirectUri(), null, CHALLENGE, "plain",
                OAUTH_STATE, SOCKET_IP, 42L, expiresAt()));
        assertThatIllegalArgumentException().isThrownBy(() -> value("core_agent", redirectUri(), null, CHALLENGE, "S256",
                "state\nheader", SOCKET_IP, 42L, expiresAt()));
        assertThatIllegalArgumentException().isThrownBy(() -> value("core_agent", redirectUri(), null, CHALLENGE, "S256",
                OAUTH_STATE, "proxy.example.test", 42L, expiresAt()));
        assertThatIllegalArgumentException().isThrownBy(() -> value("core_agent", redirectUri(), null, CHALLENGE, "S256",
                OAUTH_STATE, SOCKET_IP, 0L, expiresAt()));
        assertThatIllegalArgumentException().isThrownBy(() -> value("core_agent", redirectUri(), null, CHALLENGE, "S256",
                OAUTH_STATE, SOCKET_IP, 42L, ISSUED_AT.plus(Duration.ofMinutes(9))));
    }

    @Test
    void serializesWithoutAddingCredentialOrTokenFieldsAndRedactsSensitiveValues() throws Exception {
        CoreAgentBrowserAuthorizationTransaction transaction = transaction(List.of("note:read"));
        CoreAgentBrowserAuthorizationTransaction copy = deserialize(serialize(transaction));

        assertThat(copy).isEqualTo(transaction);
        assertThat(transaction.toString()).contains("<redacted>")
                .doesNotContain(CHALLENGE, OAUTH_STATE, SOCKET_IP, "username", "password", "rawCode", "accessToken",
                        "refreshToken");
        assertThat(Arrays.stream(CoreAgentBrowserAuthorizationTransaction.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("rawCode", "accessToken", "refreshToken", "username", "passwordHash", "email");
    }

    private static CoreAgentBrowserAuthorizationTransaction transaction(List<String> requestedScopes) {
        return value("core_agent", redirectUri(), requestedScopes, CHALLENGE, "S256", OAUTH_STATE, SOCKET_IP, 42L,
                expiresAt());
    }

    private static CoreAgentBrowserAuthorizationTransaction value(String clientId, String redirectUri,
                                                                    List<String> requestedScopes, String challenge,
                                                                    String method, String state, String socketAddress,
                                                                    long userId, Instant expiresAt) {
        return new CoreAgentBrowserAuthorizationTransaction(clientId, redirectUri, requestedScopes, challenge, method,
                state, socketAddress, userId, ISSUED_AT, expiresAt);
    }

    private static byte[] serialize(CoreAgentBrowserAuthorizationTransaction transaction) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(transaction);
        }
        return bytes.toByteArray();
    }

    private static CoreAgentBrowserAuthorizationTransaction deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (CoreAgentBrowserAuthorizationTransaction) input.readObject();
        }
    }

    private static Instant expiresAt() {
        return ISSUED_AT.plus(Duration.ofMinutes(10));
    }

    private static String redirectUri() {
        return "http://127.0.0.1:9090/oauth/callback";
    }

    private static String base64Url(byte fill) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
