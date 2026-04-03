package com.jacolp;

import com.jacolp.utils.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "")
class MiddlewareServerApplicationTests {
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${jacolp.default-password}")
    private String defaultPassword;

    @Test
    public void testPasswordEncode() {
        System.out.println(defaultPassword);
        System.out.println(passwordEncoder.encode(defaultPassword));
    }
}
