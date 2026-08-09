package com.api.atlas.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit tests for {@link EncryptionConfig#validateKey()} — no Spring context.
 * Proves the startup guard rejects the placeholder, short keys and malformed Base64
 * while accepting a valid 32-byte key.
 */
class EncryptionConfigTest {

    private static final String PLACEHOLDER = "CHANGE_ME_PLEASE_REPLACE_WITH_BASE64_32_BYTE_KEY";

    private EncryptionConfig newConfigWithSecretKey(String secretKey) {
        EncryptionConfig config = new EncryptionConfig();
        ReflectionTestUtils.setField(config, "secretKey", secretKey);
        return config;
    }

    @Test
    void validateKey_PlaceholderKey_ThrowsIllegalStateException() {
        EncryptionConfig config = newConfigWithSecretKey(PLACEHOLDER);

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateKey);
        assertEquals("atlas.encryption.secret-key must be changed from default", ex.getMessage());
    }

    @Test
    void validateKey_Short16ByteKey_ThrowsIllegalStateException() {
        // "1234567890123456" = 16 bytes, well under the required 32.
        String shortKey = Base64.getEncoder().encodeToString("1234567890123456".getBytes());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> newConfigWithSecretKey(shortKey).validateKey());
        assertEquals("atlas.encryption.secret-key must be a valid Base64 32-byte key", ex.getMessage());
    }

    @Test
    void validateKey_MalformedBase64_ThrowsIllegalStateException() {
        EncryptionConfig config = newConfigWithSecretKey("!!not-a-valid-base64!!");

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateKey);
        assertEquals("atlas.encryption.secret-key must be a valid Base64 32-byte key", ex.getMessage());
    }

    @Test
    void validateKey_Valid32ByteKey_DoesNotThrow() {
        String validKey = Base64.getEncoder().encodeToString(new byte[32]);

        assertDoesNotThrow(() -> newConfigWithSecretKey(validKey).validateKey());
    }
}
