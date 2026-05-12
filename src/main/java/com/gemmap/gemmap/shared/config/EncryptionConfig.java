package com.gemmap.gemmap.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Apple refresh_token 전용 암복호화 Bean 설정.
 * Spring Security Crypto의 Encryptors.text (AES-256-CBC + 랜덤 IV)를 재사용한다.
 */
@Configuration
public class EncryptionConfig {

    @Bean(name = "appleRefreshTokenEncryptor")
    public TextEncryptor appleRefreshTokenEncryptor(
            @Value("${crypto.apple-refresh-token.enc-password}") String password,
            @Value("${crypto.apple-refresh-token.enc-salt}") String saltHex
    ) {
        return Encryptors.text(password, saltHex);
    }
}
