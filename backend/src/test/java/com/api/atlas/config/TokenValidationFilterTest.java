package com.api.atlas.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.api.atlas.service.RedisTokenService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link TokenValidationFilter} — filter built directly, no Spring context.
 * Uses MockHttpServletRequest/Response + a mock RedisTokenService.
 */
@ExtendWith(MockitoExtension.class)
class TokenValidationFilterTest {

    @Mock
    private RedisTokenService redisTokenService;

    @Mock
    private FilterChain filterChain;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        ch.qos.logback.classic.Logger filterLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TokenValidationFilter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        filterLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        ch.qos.logback.classic.Logger filterLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TokenValidationFilter.class);
        filterLogger.detachAppender(logAppender);
    }

    private void authenticateAsJwtUser() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("testuser")
                .claim("role", "ADMIN")
                .jti("test-jti")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void doFilterInternal_RedisThrowsAndFailClosed_Returns503() throws Exception {
        doThrow(new IllegalStateException("connection refused")).when(redisTokenService).exists(anyString());
        TokenValidationFilter filter = new TokenValidationFilter(redisTokenService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/datasources");
        MockHttpServletResponse response = new MockHttpServletResponse();
        authenticateAsJwtUser();

        filter.doFilter(request, response, filterChain);

        assertEquals(503, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"code\":503"));
        assertTrue(body.contains("\"msg\":\"Token service unavailable\""));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_RedisThrowsAndFailOpen_PassesThrough() throws Exception {
        doThrow(new IllegalStateException("connection refused")).when(redisTokenService).exists(anyString());
        TokenValidationFilter filter = new TokenValidationFilter(redisTokenService, false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/datasources");
        MockHttpServletResponse response = new MockHttpServletResponse();
        authenticateAsJwtUser();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(same(request), same(response));
        assertTrue(logAppender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("Redis unavailable, allowing request through")),
                "expected WARN log about degraded Redis");
    }

    @Test
    void doFilterInternal_RevokedToken_Returns401() throws Exception {
        when(redisTokenService.exists(anyString())).thenReturn(false);
        TokenValidationFilter filter = new TokenValidationFilter(redisTokenService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/datasources");
        MockHttpServletResponse response = new MockHttpServletResponse();
        authenticateAsJwtUser();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"code\":401"));
        assertTrue(body.contains("\"msg\":\"Token has been revoked\""));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_OptionsRequest_PassesThroughWithoutJtiCheck() throws Exception {
        TokenValidationFilter filter = new TokenValidationFilter(redisTokenService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/datasources");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(same(request), same(response));
        verify(redisTokenService, never()).exists(anyString());
    }

    @Test
    void doFilterInternal_LoginEndpoint_PassesThroughWithoutJtiCheck() throws Exception {
        TokenValidationFilter filter = new TokenValidationFilter(redisTokenService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(same(request), same(response));
        verify(redisTokenService, never()).exists(anyString());
    }
}
