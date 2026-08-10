package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeState;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/** Strict version-one Map codec for future Redis/Lua storage. */
public final class EmailLoginCodeStateCodec {
    private static final List<String> FIELDS = List.of("schema_version", "client_id", "user_id", "email_fingerprint",
            "verifier_hash", "failed_attempts", "issued_at_epoch_millis", "expires_at_epoch_millis");
    public List<String> fieldNames() { return FIELDS; }
    public Map<String, Object> encode(EmailLoginCodeState state) {
        if (state == null) throw new IllegalArgumentException("Invalid email-code state map");
        Map<String,Object> map = new LinkedHashMap<>();
        map.put("schema_version", 1); map.put("client_id", state.clientId()); map.put("user_id", state.userId());
        map.put("email_fingerprint", state.emailFingerprint()); map.put("verifier_hash", state.verifierHash());
        map.put("failed_attempts", state.failedAttempts()); map.put("issued_at_epoch_millis", state.issuedAt().toEpochMilli());
        map.put("expires_at_epoch_millis", state.expiresAt().toEpochMilli()); return Collections.unmodifiableMap(map);
    }
    public EmailLoginCodeState decode(Map<String, ?> map) {
        if (map == null || !map.keySet().equals(java.util.Set.copyOf(FIELDS))) throw invalid();
        try { if (integer(map,"schema_version") != 1) throw invalid(); return new EmailLoginCodeState(string(map,"client_id"), longValue(map,"user_id"), string(map,"email_fingerprint"),
                string(map,"verifier_hash"), integer(map,"failed_attempts"), Instant.ofEpochMilli(longValue(map,"issued_at_epoch_millis")),
                Instant.ofEpochMilli(longValue(map,"expires_at_epoch_millis"))); } catch (RuntimeException e) { throw invalid(); }
    }
    private static String string(Map<String,?> map,String key) { Object value=map.get(key); if (!(value instanceof String text)) throw invalid(); return text; }
    private static int integer(Map<String,?> map,String key) { Object value=map.get(key); if (!(value instanceof Integer number)) throw invalid(); return number; }
    private static long longValue(Map<String,?> map,String key) { Object value=map.get(key); if (!(value instanceof Long number)) throw invalid(); return number; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid email-code state map"); }
}
