package com.api.atlas.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit tests for {@link RsaKeyConfig#validateKey()} — no Spring context.
 * Proves the startup guard rejects placeholder/short/missing-BEGIN PEMs while
 * accepting a real generated RSA-2048 keypair.
 */
class RsaKeyConfigTest {

    private static final String PRIVATE_PLACEHOLDER = "CHANGE_ME_PLEASE_REPLACE_WITH_RSA_PRIVATE_KEY_PEM";
    private static final String PUBLIC_PLACEHOLDER = "CHANGE_ME_PLEASE_REPLACE_WITH_RSA_PUBLIC_KEY_PEM";

    private RsaKeyConfig newConfigWithKeys(String privateKey, String publicKey) {
        RsaKeyConfig config = new RsaKeyConfig();
        ReflectionTestUtils.setField(config, "privateKey", privateKey);
        ReflectionTestUtils.setField(config, "publicKey", publicKey);
        return config;
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate test RSA keypair", e);
        }
    }

    private String toPem(String beginLabel, String endLabel, byte[] der) {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return beginLabel + "\n" + body + "\n" + endLabel;
    }

    @Test
    void validateKey_PlaceholderPrivateKey_ThrowsIllegalStateException() {
        KeyPair keyPair = generateKeyPair();
        RsaKeyConfig config = newConfigWithKeys(PRIVATE_PLACEHOLDER,
                toPem("-----BEGIN PUBLIC KEY-----", "-----END PUBLIC KEY-----", keyPair.getPublic().getEncoded()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateKey);
        assertEquals("atlas.jwt.private-key must be changed from default", ex.getMessage());
    }

    @Test
    void validateKey_PlaceholderPublicKey_ThrowsIllegalStateException() {
        KeyPair keyPair = generateKeyPair();
        RsaKeyConfig config = newConfigWithKeys(
                toPem("-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----", keyPair.getPrivate().getEncoded()),
                PUBLIC_PLACEHOLDER);

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateKey);
        assertEquals("atlas.jwt.public-key must be changed from default", ex.getMessage());
    }

    @Test
    void validateKey_ShortPrivateKey_ThrowsIllegalStateException() {
        KeyPair keyPair = generateKeyPair();
        RsaKeyConfig config = newConfigWithKeys("too-short-to-be-a-pem",
                toPem("-----BEGIN PUBLIC KEY-----", "-----END PUBLIC KEY-----", keyPair.getPublic().getEncoded()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateKey);
        assertEquals("atlas.jwt.private-key must be a valid PEM-encoded RSA private key", ex.getMessage());
    }

    @Test
    void validateKey_MissingBeginPrivateKey_ThrowsIllegalStateException() {
        KeyPair keyPair = generateKeyPair();
        // Long enough but lacks the -----BEGIN marker.
        String fakePrivateKey = "P".repeat(250);
        RsaKeyConfig config = newConfigWithKeys(fakePrivateKey,
                toPem("-----BEGIN PUBLIC KEY-----", "-----END PUBLIC KEY-----", keyPair.getPublic().getEncoded()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateKey);
        assertEquals("atlas.jwt.private-key must be a valid PEM-encoded RSA private key", ex.getMessage());
    }

    @Test
    void validateKey_MissingBeginPublicKey_ThrowsIllegalStateException() {
        KeyPair keyPair = generateKeyPair();
        String fakePublicKey = "Q".repeat(250);
        RsaKeyConfig config = newConfigWithKeys(
                toPem("-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----", keyPair.getPrivate().getEncoded()),
                fakePublicKey);

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateKey);
        assertEquals("atlas.jwt.public-key must be a valid PEM-encoded RSA public key", ex.getMessage());
    }

    @Test
    void validateKey_ValidKeyPair_DoesNotThrow() {
        KeyPair keyPair = generateKeyPair();
        RsaKeyConfig config = newConfigWithKeys(
                toPem("-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----", keyPair.getPrivate().getEncoded()),
                toPem("-----BEGIN PUBLIC KEY-----", "-----END PUBLIC KEY-----", keyPair.getPublic().getEncoded()));

        assertDoesNotThrow(config::validateKey);
    }
}
