package com.api.atlas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class RsaKeyConfig {

    @Value("${atlas.jwt.private-key}")
    private String privateKey;

    @Value("${atlas.jwt.public-key}")
    private String publicKey;

    @PostConstruct
    public void validateKey() {
        if ("CHANGE_ME_PLEASE_REPLACE_WITH_RSA_PRIVATE_KEY_PEM".equals(privateKey)) {
            throw new IllegalStateException("atlas.jwt.private-key must be changed from default");
        }
        if ("CHANGE_ME_PLEASE_REPLACE_WITH_RSA_PUBLIC_KEY_PEM".equals(publicKey)) {
            throw new IllegalStateException("atlas.jwt.public-key must be changed from default");
        }
        if (privateKey.length() < 200 || !privateKey.contains("-----BEGIN")) {
            throw new IllegalStateException("atlas.jwt.private-key must be a valid PEM-encoded RSA private key");
        }
        if (publicKey.length() < 200 || !publicKey.contains("-----BEGIN")) {
            throw new IllegalStateException("atlas.jwt.public-key must be a valid PEM-encoded RSA public key");
        }
    }

    @Bean
    public RSAPrivateKey rsaPrivateKey() {
        return (RSAPrivateKey) RsaKeyConverters.pkcs8()
                .convert(new ByteArrayInputStream(privateKey.getBytes(StandardCharsets.UTF_8)));
    }

    @Bean
    public RSAPublicKey rsaPublicKey() {
        return (RSAPublicKey) RsaKeyConverters.x509()
                .convert(new ByteArrayInputStream(publicKey.getBytes(StandardCharsets.UTF_8)));
    }
}
