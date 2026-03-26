package com.techtutter.ratelimiter.interceptor;

import com.techtutter.ratelimiter.annotation.RateLimit;
import com.techtutter.ratelimiter.exception.RateLimitExceededException;
import com.techtutter.ratelimiter.service.RateLimiterService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimiterInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterInterceptor.class);
    private final RateLimiterService rateLimiterService;
    private final Counter allowedCounter;
    private final Counter blockedCounter;
    private final Counter redisErrorCounter;
    private final Timer redisLatencyTimer;

    public RateLimiterInterceptor(RateLimiterService rateLimiterService, MeterRegistry meterRegistry) {
        this.rateLimiterService = rateLimiterService;
        this.allowedCounter = meterRegistry.counter("rate_limit.allowed");
        this.blockedCounter = meterRegistry.counter("rate_limit.blocked");
        this.redisErrorCounter = meterRegistry.counter("rate_limit.redis_errors");
        this.redisLatencyTimer = meterRegistry.timer("rate_limit.redis_latency");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (handler instanceof HandlerMethod handlerMethod) {
            RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);

            if (rateLimit != null) {
                // Extract IP or X-API-Key (standard IP approach)
                String clientIp = request.getHeader("X-Forwarded-For");
                if (clientIp == null || clientIp.isEmpty()) {
                    clientIp = request.getRemoteAddr();
                }

                String key = "ratelimit:" + clientIp + ":" + request.getRequestURI();
                int limit = rateLimit.requests();
                int window = rateLimit.window();

                try {
                    boolean allowed = redisLatencyTimer
                            .recordCallable(() -> rateLimiterService.isAllowed(key, limit, window));

                    if (!allowed) {
                        blockedCounter.increment();
                        throw new RateLimitExceededException("Rate limit exceeded for the resource.");
                    }

                    allowedCounter.increment();
                    long remaining = rateLimiterService.getApproximateRemaining(key, limit);
                    response.addHeader("X-RateLimit-Limit", String.valueOf(limit));
                    response.addHeader("X-RateLimit-Remaining", String.valueOf(remaining));

                } catch (RateLimitExceededException e) {
                    throw e; // Let the GlobalExceptionHandler handle the error
                } catch (Exception e) {
                    redisErrorCounter.increment();
                    // Log the error but let the request pass by returning true.
                    log.warn("Rate Limiter failed (Redis down?). Fail-Open activated for the request. Reason: {}",
                            e.getMessage());
                }
            }
        }
        return true;
    }
}
