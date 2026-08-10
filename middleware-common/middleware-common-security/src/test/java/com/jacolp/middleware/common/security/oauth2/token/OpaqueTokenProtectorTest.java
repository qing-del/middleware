package com.jacolp.middleware.common.security.oauth2.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OpaqueTokenProtectorTest {
    private static final String RAW = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private final OpaqueTokenProtector protector = new OpaqueTokenProtector();

    @Test void protectsWithStableFingerprintAndSaltedVerifier() {
        OpaqueTokenProtection first = protector.protect(RAW);
        OpaqueTokenProtection second = protector.protect(RAW);
        assertThat(first.fingerprint()).hasSize(43).doesNotContain("=").isEqualTo(second.fingerprint());
        assertThat(first.verifierHash()).isNotEqualTo(second.verifierHash()).doesNotContain(RAW);
        assertThat(protector.matches(RAW, first.verifierHash())).isTrue();
        assertThat(protector.matches(RAW, second.verifierHash())).isTrue();
        assertThat(first.toString()).doesNotContain(RAW).doesNotContain(first.verifierHash());
    }

    @Test void rejectsInvalidRawTokensAndSafelyRejectsBadHashes() {
        assertThatIllegalArgumentException().isThrownBy(() -> protector.protect(null));
        assertThatIllegalArgumentException().isThrownBy(() -> protector.fingerprint("short"));
        assertThatIllegalArgumentException().isThrownBy(() -> protector.matches("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh+", "x"));
        assertThat(protector.matches(RAW, null)).isFalse();
        assertThat(protector.matches(RAW, "not-a-bcrypt-hash")).isFalse();
    }
}
