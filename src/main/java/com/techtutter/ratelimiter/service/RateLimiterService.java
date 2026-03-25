package com.techtutter.ratelimiter.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> redisScript;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setLocation(new ClassPathResource("sliding_window_rate_limit.lua"));
        this.redisScript.setResultType(Long.class);
    }

    /**
     * Checks whether a request is allowed based on the sliding window rate limit.
     *
     * @param key             The unique identifier for the rate limit (e.g., client
     *                        IP or API Key).
     * @param limit           The maximum number of allowed requests.
     * @param windowInSeconds The sliding window size in seconds.
     * @return true if the request is allowed, false if the rate limit is exceeded.
     */
    public boolean isAllowed(String key, int limit, int windowInSeconds) {
        long currentTimeMillis = Instant.now().toEpochMilli();
        long windowSizeMillis = windowInSeconds * 1000L;

        // Unique element in the sorted set (timestamp alone is not enough for elements
        // of the same millisecond fraction)
        String uniqueElement = currentTimeMillis + "-" + UUID.randomUUID().toString();

        Long result = redisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(currentTimeMillis),
                String.valueOf(windowSizeMillis),
                String.valueOf(limit),
                uniqueElement);

        return result != null && result == 1L;
    }

    /**
     * Calculates an approximate remaining request pool based on current state.
     * Since checking is inherently separate from the execution transaction,
     * this is an approximation used solely for 'X-RateLimit-Remaining' Header
     * output purposes.
     */
    public long getApproximateRemaining(String key, int limit) {
        Long currentRequests = redisTemplate.opsForZSet().zCard(key);
        return Math.max(0, limit - (currentRequests != null ? currentRequests : 0));
    }
}
