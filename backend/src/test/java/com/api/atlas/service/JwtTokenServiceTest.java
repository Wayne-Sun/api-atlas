package com.api.atlas.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();

        jwtTokenService = new JwtTokenService(privateKey, publicKey, 1800, 604800);
    }

    @Test
    void generateAccessToken_ValidInput_ReturnsJwt() {
        String token = jwtTokenService.generateAccessToken("testuser", "ADMIN");

        assertNotNull(token);
        assertFalse(token.isBlank());
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 dot-separated parts");

        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        Jwt jwt = decoder.decode(token);

        assertEquals("testuser", jwt.getSubject());
        assertEquals("ADMIN", jwt.getClaimAsString("role"));
        assertNotNull(jwt.getExpiresAt());
        assertTrue(jwt.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void generateRefreshToken_ValidInput_ReturnsJwt() {
        String token = jwtTokenService.generateRefreshToken("testuser");

        assertNotNull(token);
        assertFalse(token.isBlank());
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 dot-separated parts");

        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        Jwt jwt = decoder.decode(token);

        assertEquals("testuser", jwt.getSubject());
        assertNotNull(jwt.getExpiresAt());
        assertTrue(jwt.getExpiresAt().isAfter(Instant.now()));
    }
}
