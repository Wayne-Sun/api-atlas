package com.api.atlas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import jakarta.annotation.PostConstruct;
import java.util.Base64;

@Configuration
public class EncryptionConfig {

    @Value("${atlas.encryption.secret-key}")
    private String secretKey;

    @PostConstruct
    public void validateKey() {
        if ("CHANGE_ME_PLEASE_REPLACE_WITH_BASE64_32_BYTE_KEY".equals(secretKey)) {
            throw new IllegalStateException("atlas.encryption.secret-key must be changed from default");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secretKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("atlas.encryption.secret-key must be a valid Base64 32-byte key", e);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("atlas.encryption.secret-key must be a valid Base64 32-byte key");
        }
    }

    @Bean
    public SecretKey encryptionKey() {
        byte[] decoded = Base64.getDecoder().decode(secretKey);
        return new SecretKeySpec(decoded, "AES");
    }
}
