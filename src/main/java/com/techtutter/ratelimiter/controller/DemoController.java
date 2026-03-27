package com.techtutter.ratelimiter.controller;

import com.techtutter.ratelimiter.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoController {

    /**
     * Demo endpoint for load testing with k6.
     */
    @GetMapping("/hello")
    @RateLimit(requests = 50, window = 10)
    public String sayHello() {
        return "Hello, World!";
    }
}
