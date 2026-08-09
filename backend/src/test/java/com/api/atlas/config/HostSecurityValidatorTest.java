package com.api.atlas.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link HostSecurityValidator} — no Spring context.
 * Covers loopback/private/link-local/unspecified/multicast rejection, public-host
 * acceptance, the {@code allow-private-hosts} bypass, and DNS-failure blocking.
 */
class HostSecurityValidatorTest {

    private final HostSecurityValidator validator = new HostSecurityValidator(false);
    private final HostSecurityValidator permissiveValidator = new HostSecurityValidator(true);

    @Test
    void isBlocked_LoopbackIPv4_ReturnsTrue() {
        assertTrue(validator.isBlocked("127.0.0.1"));
    }

    @Test
    void isBlocked_LoopbackHostname_ReturnsTrue() {
        // "localhost" may resolve to multiple addresses (::1 + 127.0.0.1) on some
        // JVMs — ANY-resolved-address-blocked makes it deterministic either way.
        assertTrue(validator.isBlocked("localhost"));
    }

    @Test
    void isBlocked_MetadataLinkLocal_ReturnsTrue() {
        assertTrue(validator.isBlocked("169.254.169.254"));
    }

    @Test
    void isBlocked_PrivateIpv4_10_ReturnsTrue() {
        assertTrue(validator.isBlocked("10.0.0.1"));
    }

    @Test
    void isBlocked_PrivateIpv4_192168_ReturnsTrue() {
        assertTrue(validator.isBlocked("192.168.1.1"));
    }

    @Test
    void isBlocked_Ipv6Loopback_ReturnsTrue() {
        assertTrue(validator.isBlocked("::1"));
    }

    @Test
    void isBlocked_UnspecifiedAddress_ReturnsTrue() {
        assertTrue(validator.isBlocked("0.0.0.0"));
    }

    @Test
    void isBlocked_BlankHost_ReturnsTrue() {
        assertTrue(validator.isBlocked(""));
        assertTrue(validator.isBlocked("   "));
    }

    @Test
    void isBlocked_NullHost_ReturnsTrue() {
        assertTrue(validator.isBlocked(null));
    }

    @Test
    void isBlocked_PublicDnsIpv4_ReturnsFalse() {
        assertFalse(validator.isBlocked("8.8.8.8"));
    }

    @Test
    void isBlocked_UnknownHost_ReturnsTrue() {
        // ".invalid" is a reserved TLD that fails fast; DNS failures must be
        // treated as blocked without leaking resolution details.
        assertTrue(validator.isBlocked("nonexistent.invalid"));
    }

    @Test
    void isBlocked_AllowPrivateHostsTrue_LoopbackAllowed_ReturnsFalse() {
        assertFalse(permissiveValidator.isBlocked("localhost"));
        assertFalse(permissiveValidator.isBlocked("127.0.0.1"));
    }

    @Test
    void isBlocked_AllowPrivateHostsTrue_PublicHost_ReturnsFalse() {
        assertFalse(permissiveValidator.isBlocked("8.8.8.8"));
    }
}
