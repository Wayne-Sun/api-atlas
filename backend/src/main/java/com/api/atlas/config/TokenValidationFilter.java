package com.api.atlas.config;

import com.api.atlas.service.RedisTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates that the JWT token's jti (token ID) still exists in Redis.
 * If the token has been revoked (removed from Redis), the request is rejected.
 * Falls back to allowing the request through if Redis is unavailable.
 */
public class TokenValidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenValidationFilter.class);

    private final RedisTokenService redisTokenService;

    public TokenValidationFilter(RedisTokenService redisTokenService) {
        this.redisTokenService = redisTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip OPTIONS preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip login endpoint
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/login")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Skip if not a JWT-authenticated request
        if (!(authentication instanceof JwtAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();
        String jti = jwt.getId();
        if (jti == null) {
            jti = jwt.getClaimAsString("jti");
        }

        try {
            boolean exists = redisTokenService.exists(jti);
            if (!exists) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"msg\":\"Token has been revoked\"}");
                return;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable, allowing request through: {} - {}", path, e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
