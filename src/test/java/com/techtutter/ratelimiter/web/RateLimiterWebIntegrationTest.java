package com.techtutter.ratelimiter.web;

import com.techtutter.ratelimiter.annotation.RateLimit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RateLimiterWebIntegrationTest.TestConfig.class)
class RateLimiterWebIntegrationTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class DummyController {
        @GetMapping("/api/test-rate-limit")
        @RateLimit(requests = 2, window = 10)
        public String testEndpoint() {
            return "OK";
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public DummyController dummyController() {
            return new DummyController();
        }
    }

    @Test
    void testRateLimiterWebLayerIsBlockingExtceedingRequestsAndForwardingCorrectHeaders() throws Exception {
        // Arrange is done by @RateLimit(requests=2)
        String url = "/api/test-rate-limit";
        String clientIp = "127.0.0.1";

        // Act & Assert 1
        mockMvc.perform(get(url).header("X-Forwarded-For", clientIp))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(content().string("OK"));

        // Act & Assert 2 
        mockMvc.perform(get(url).header("X-Forwarded-For", clientIp))
                .andExpect(status().isOk());

        // Act & Assert 3: Questa richiesta deve finire in 429
        mockMvc.perform(get(url).header("X-Forwarded-For", clientIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
