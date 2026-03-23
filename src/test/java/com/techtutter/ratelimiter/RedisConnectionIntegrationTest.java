package com.techtutter.ratelimiter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test to verify Redis connectivity using Testcontainers.
 */
@SpringBootTest
@Testcontainers
class RedisConnectionIntegrationTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void testRedisConnectionAndWrite() {
        // Arrange
        String key = "test-key";
        String value = "L6-Ready";

        // Act
        redisTemplate.opsForValue().set(key, value);
        String retrievedValue = redisTemplate.opsForValue().get(key);

        // Assert
        Assertions.assertEquals(value, retrievedValue, "The value read from Redis should match the value written.");
    }
}
