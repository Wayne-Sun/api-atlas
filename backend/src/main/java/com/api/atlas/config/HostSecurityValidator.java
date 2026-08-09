package com.api.atlas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * SSRF guard for outbound datasource connections.
 *
 * <p>Rejects hosts that resolve to loopback, private (RFC 1918 / IPv6 ULA),
 * link-local, unspecified, or multicast addresses. DNS resolution failures are
 * treated as blocked so resolution errors are never leaked to callers. The whole
 * check is bypassed when {@code atlas.security.allow-private-hosts} is
 * {@code true} (operators running this against in-house/internal databases).</p>
 *
 * <p><strong>Documented residual (admin-trusted):</strong> validation resolves
 * the host at check time; a factory connecting to the same hostname re-resolves
 * DNS, so a rebinding host could pass validation and later resolve to a private
 * address. All client-creation paths and {@code /datasources/test-connection} are
 * ADMIN-only (see the security-remediation plan), so this is accepted — we do NOT
 * resolve-and-pin connections.</p>
 */
@Component
public class HostSecurityValidator {

    private final boolean allowPrivateHosts;

    public HostSecurityValidator(@Value("${atlas.security.allow-private-hosts:false}") boolean allowPrivateHosts) {
        this.allowPrivateHosts = allowPrivateHosts;
    }

    /**
     * @return {@code true} when {@code host} must not be used for an outbound
     *         connection (blank, unresolvable, or resolving to a restricted
     *         address), {@code false} when the host is acceptable.
     */
    public boolean isBlocked(String host) {
        if (allowPrivateHosts) {
            return false;
        }
        if (host == null || host.isBlank()) {
            return true;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isRestrictedAddress(address)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            // Never leak resolution details to the client — treat as blocked.
            return true;
        }
    }

    private boolean isRestrictedAddress(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress();
    }
}
