package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeState;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class EmailLoginCodeStateCodecTest {
    private final EmailLoginCodeStateCodec codec = new EmailLoginCodeStateCodec();
    private final EmailLoginCodeState state = new EmailLoginCodeState("user", 7L, "A".repeat(43),
            "$2a$10$" + "a".repeat(53), 4, Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(2_000));
    @Test void roundTripIsStableAndImmutable() {
        Map<String,Object> map=codec.encode(state); assertThat(map.keySet()).containsExactlyElementsOf(codec.fieldNames());
        assertThat(codec.decode(map)).isEqualTo(state); assertThatThrownBy(() -> map.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void invalidSchemaAndFieldsFailClosed() {
        Map<String,Object> map=new java.util.LinkedHashMap<>(codec.encode(state)); map.put("schema_version", 2);
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(map)); map=new java.util.LinkedHashMap<>(codec.encode(state)); map.remove("client_id");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(map)); map=new java.util.LinkedHashMap<>(codec.encode(state)); map.put("extra", "x");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(map));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeState("user",7L,"bad","bad",0,Instant.EPOCH,Instant.EPOCH));
    }
}
