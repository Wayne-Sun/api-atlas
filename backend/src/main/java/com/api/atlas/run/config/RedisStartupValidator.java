package com.api.atlas.run.config;

import com.api.atlas.model.TokenSession;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Validates Redis connectivity on application startup.
 * Logs a warning if Redis is unavailable — does NOT block startup so that
 * the application can still serve requests (with degraded token revocation).
 */
@Component
public class RedisStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(RedisStartupValidator.class);

    private final RedisTemplate<String, TokenSession> redisTemplate;

    public RedisStartupValidator(@Qualifier("redisTokenTemplate") RedisTemplate<String, TokenSession> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void validate() {
        try {
            redisTemplate.opsForValue().get("healthcheck");
            log.info("Redis connection verified successfully");
        } catch (Exception e) {
            log.warn("Redis is unavailable — token revocation will be degraded. Cause: {}", e.getMessage());
        }
    }
}
