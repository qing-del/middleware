package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeIssueRateLimitRequest;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeIssueRateLimitDecision;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeIssueRateLimiter;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;

class EmailLoginCodeIssueRateLimitRequestTest {
    private static final String EMAIL = "A".repeat(43); private static final String IP = "B".repeat(43);
    @Test void acceptsDefaultAndStricterLimits() { assertThat(request(Duration.ofSeconds(60),Duration.ofHours(1),5)).isNotNull(); assertThat(request(Duration.ofMinutes(2),Duration.ofHours(2),1)).isNotNull(); }
    @Test void rejectsInvalidFieldsAndDoesNotLeakFingerprints() { assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeIssueRateLimitRequest(null,IP,Duration.ofMinutes(1),Duration.ofHours(1),1)); assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeIssueRateLimitRequest("bad",IP,Duration.ofMinutes(1),Duration.ofHours(1),1)); assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeIssueRateLimitRequest(EMAIL,null,Duration.ofMinutes(1),Duration.ofHours(1),1)); assertThatIllegalArgumentException().isThrownBy(() -> request(Duration.ofSeconds(59),Duration.ofHours(1),1)); assertThatIllegalArgumentException().isThrownBy(() -> request(Duration.ofMinutes(1),Duration.ofMinutes(59),1)); assertThatIllegalArgumentException().isThrownBy(() -> request(Duration.ofHours(2),Duration.ofHours(1),1)); assertThatIllegalArgumentException().isThrownBy(() -> request(Duration.ofMinutes(1),Duration.ofHours(1),0)); assertThatIllegalArgumentException().isThrownBy(() -> request(Duration.ofMinutes(1),Duration.ofHours(1),6)); assertThat(request(Duration.ofMinutes(1),Duration.ofHours(1),1).toString()).doesNotContain(EMAIL,IP); }
    @Test void portHasNoInfrastructureTypesAndDecisionOrderIsStable() { assertThat(EmailLoginCodeIssueRateLimiter.class.getDeclaredMethods()[0].getGenericReturnType().getTypeName()).doesNotContain(".infrastructure.","DO"); assertThat(EmailLoginCodeIssueRateLimitDecision.values()).containsExactly(EmailLoginCodeIssueRateLimitDecision.ALLOWED,EmailLoginCodeIssueRateLimitDecision.COOLDOWN,EmailLoginCodeIssueRateLimitDecision.WINDOW_LIMIT); }
    private static EmailLoginCodeIssueRateLimitRequest request(Duration cooldown,Duration window,Integer max){return new EmailLoginCodeIssueRateLimitRequest(EMAIL,IP,cooldown,window,max);}
}
