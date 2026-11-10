package edu.swu.agrimart.user.account.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** 验证账户密码编码配置，沿用 Argon2 摘要算法的测试。 */
class AuthConfigTest {
    /** 验证密码采用随机盐 Argon2 编码并拒绝错误密码。 */
    @Test
    void encoderUsesSaltedArgon2AndRejectsWrongPasswords() {
        var encoder = new AuthConfig().passwordEncoder();
        assertThat(encoder).isInstanceOf(Argon2PasswordEncoder.class);
        String first = encoder.encode("secret123");
        String second = encoder.encode("secret123");
        assertThat(first).startsWith("$argon2id$").isNotEqualTo(second);
        assertThat(encoder.matches("secret123", first)).isTrue();
        assertThat(encoder.matches("wrong", first)).isFalse();
        assertThat(encoder.matches("secret123", "malformed")).isFalse();
    }
}
