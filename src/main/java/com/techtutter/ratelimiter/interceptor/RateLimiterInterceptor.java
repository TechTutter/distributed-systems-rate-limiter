package com.techtutter.ratelimiter.interceptor;

import com.techtutter.ratelimiter.annotation.RateLimit;
import com.techtutter.ratelimiter.exception.RateLimitExceededException;
import com.techtutter.ratelimiter.service.RateLimiterService;
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

    public RateLimiterInterceptor(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
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
                    boolean allowed = rateLimiterService.isAllowed(key, limit, window);
                    if (!allowed) {
                        throw new RateLimitExceededException("Rate limit exceeded for the resource.");
                    }

                    long remaining = rateLimiterService.getApproximateRemaining(key, limit);
                    response.addHeader("X-RateLimit-Limit", String.valueOf(limit));
                    response.addHeader("X-RateLimit-Remaining", String.valueOf(remaining));

                } catch (RateLimitExceededException e) {
                    throw e; // Let the GlobalExceptionHandler handle the error
                } catch (Exception e) {
                    // Log the error but let the request pass by returning true.
                    log.warn("Rate Limiter failed (Redis down?). Fail-Open activated for the request. Reason: {}",
                            e.getMessage());
                }
            }
        }
        return true;
    }
}
