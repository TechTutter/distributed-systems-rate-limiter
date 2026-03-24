package com.techtutter.ratelimiter.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@Testcontainers
class RateLimiterServiceIntegrationTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private RateLimiterService rateLimiterService;

    @Test
    void testRateLimiterBlocksRequestsOverLimit() {
        // Arrange
        String key = "test-sequential-block";
        int limit = 5;
        int windowInSeconds = 10;

        for (int i = 0; i < limit; i++) {
            rateLimiterService.isAllowed(key, limit, windowInSeconds);
        }

        // Act
        boolean allowed = rateLimiterService.isAllowed(key, limit, windowInSeconds);

        // Assert
        Assertions.assertFalse(allowed, "The sixth request should be blocked because the limit is exceeded.");
    }

    @Test
    void testRateLimiterAllowsRequestsUnderLimit() {
        // Arrange
        String key = "test-sequential-allow";
        int limit = 5;
        int windowInSeconds = 10;

        // Act
        boolean allowed1 = rateLimiterService.isAllowed(key, limit, windowInSeconds);
        boolean allowed2 = rateLimiterService.isAllowed(key, limit, windowInSeconds);

        // Assert
        Assertions.assertTrue(allowed1, "The first request under the limit should pass.");
        Assertions.assertTrue(allowed2, "The second request under the limit should pass.");
    }

    @Test
    void testConcurrentRequestsAreAtomicallyHandled() throws InterruptedException {
        // Arrange
        String key = "test-concurrent-key";
        int limit = 10;
        int windowInSeconds = 5;
        int totalRequests = 500;

        ExecutorService executorService = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger deniedCount = new AtomicInteger();

        // Act
        for (int i = 0; i < totalRequests; i++) {
            executorService.submit(() -> {
                try {
                    boolean allowed = rateLimiterService.isAllowed(key, limit, windowInSeconds);
                    if (allowed) {
                        successCount.incrementAndGet();
                    } else {
                        deniedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        // Assert
        Assertions.assertEquals(limit, successCount.get(),
                "Exactly " + limit + " requests should pass without race conditions.");
        Assertions.assertEquals(totalRequests - limit, deniedCount.get(),
                "Exactly " + (totalRequests - limit) + " requests should be denied.");
    }
}
