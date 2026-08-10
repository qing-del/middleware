package com.jacolp.module.system.biz.infrastructure.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;

/** Generates unbiased six-digit decimal email login codes. */
@Component
public final class SecureEmailLoginCodeGenerator {
    private static final int CODE_BOUND = 1_000_000;
    private final SecureRandom secureRandom;

    public SecureEmailLoginCodeGenerator() {
        this(new SecureRandom());
    }

    SecureEmailLoginCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public String generate() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(CODE_BOUND));
    }
}
