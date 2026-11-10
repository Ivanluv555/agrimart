package edu.swu.agrimart.user.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 账户密码编码配置，沿用 Argon2 摘要算法。 */
@Configuration
public class AuthConfig {
    /**
     * 提供兼容现有密码摘要的 Argon2 编码器。
     *
     * @return 使用随机盐的 Argon2 密码编码器
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
