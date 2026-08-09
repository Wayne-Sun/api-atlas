package com.api.atlas.service;

import com.api.atlas.model.TokenSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisTokenService {

    private static final String KEY_PREFIX = "token:";

    private final RedisTemplate<String, TokenSession> redisTemplate;

    public RedisTokenService(@Qualifier("redisTokenTemplate") RedisTemplate<String, TokenSession> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveToken(String jti, TokenSession session, long ttlSeconds) {
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, session, ttlSeconds, TimeUnit.SECONDS);
    }

    public TokenSession getToken(String jti) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + jti);
    }

    public void removeToken(String jti) {
        redisTemplate.delete(KEY_PREFIX + jti);
    }

    public boolean exists(String jti) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + jti) != null;
    }

    public void revokeAll() {
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
