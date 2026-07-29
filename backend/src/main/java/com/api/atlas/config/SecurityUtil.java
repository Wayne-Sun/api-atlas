package com.api.atlas.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility for retrieving the current username from the security context.
 * Not a Spring bean — provides a static method usable from anywhere.
 */
public class SecurityUtil {

    private static final String SYSTEM_USER = "SYSTEM";

    private SecurityUtil() {
        // utility class — no instantiation
    }

    /**
     * Returns the current authenticated username, or {@code "SYSTEM"} if no
     * authentication context is available (e.g. during startup / DataInitializer).
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return SYSTEM_USER;
        }
        return authentication.getName();
    }
}
