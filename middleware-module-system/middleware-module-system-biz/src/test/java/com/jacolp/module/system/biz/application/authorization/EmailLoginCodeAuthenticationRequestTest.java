package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeAuthenticationRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EmailLoginCodeAuthenticationRequestTest {

    @Test
    void acceptsStrictEmailAndSixDigitAsciiCodeWithoutLeakingEither() {
        EmailLoginCodeAuthenticationRequest request =
                new EmailLoginCodeAuthenticationRequest("alice@example.test", "012345");

        assertThat(request).isNotNull();
        assertThat(request.toString()).doesNotContain("alice", "012345", ".infrastructure.");
    }

    @Test
    void rejectsInvalidEmailAndCode() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new EmailLoginCodeAuthenticationRequest(" alice@example.test", "012345"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new EmailLoginCodeAuthenticationRequest("alice @example.test", "012345"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new EmailLoginCodeAuthenticationRequest("alice@example.test", "１２３４５６"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new EmailLoginCodeAuthenticationRequest("alice@example.test", "12345"));
    }
}
