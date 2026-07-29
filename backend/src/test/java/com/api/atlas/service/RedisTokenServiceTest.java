package com.api.atlas.service;

import com.api.atlas.model.TokenSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisTokenServiceTest {

    @Mock
    private RedisTemplate<String, TokenSession> redisTemplate;

    @Mock
    private ValueOperations<String, TokenSession> valueOps;

    @InjectMocks
    private RedisTokenService redisTokenService;

    private TokenSession session;

    @BeforeEach
    void setUp() {
        session = new TokenSession(1L, "testuser", "ADMIN", LocalDateTime.now());
    }

    @Test
    void saveToken_ValidData_TokenStored() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        redisTokenService.saveToken("test-jti", session, 1800);

        verify(valueOps).set("token:test-jti", session, 1800, TimeUnit.SECONDS);
    }

    @Test
    void getToken_ExistingToken_ReturnsSession() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("token:test-jti")).thenReturn(session);

        TokenSession result = redisTokenService.getToken("test-jti");

        assertEquals(session, result);
        verify(valueOps).get("token:test-jti");
    }

    @Test
    void removeToken_ExistingToken_TokenRemoved() {
        redisTokenService.removeToken("test-jti");

        verify(redisTemplate).delete("token:test-jti");
    }

    @Test
    void exists_ExistingToken_ReturnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("token:test-jti")).thenReturn(session);

        assertTrue(redisTokenService.exists("test-jti"));
    }

    @Test
    void exists_NonExistingToken_ReturnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("token:nonexistent")).thenReturn(null);

        assertFalse(redisTokenService.exists("nonexistent"));
    }
}
