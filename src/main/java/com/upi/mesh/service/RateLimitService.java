package com.upi.mesh.service;

import com.upi.mesh.exception.RateLimitException;
import io.github.bucket4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP and per-user rate limiting using Bucket4j token bucket algorithm.
 * Production: plug this into Redis-backed Bucket4j for distributed rate limiting.
 */
@Service
public class RateLimitService {

    @Value("${upi.ratelimit.bridge-requests-per-minute:60}")
    private int bridgeRpm;

    @Value("${upi.ratelimit.auth-requests-per-minute:10}")
    private int authRpm;

    private final Map<String, Bucket> bridgeBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> authBuckets   = new ConcurrentHashMap<>();

    public void checkBridgeLimit(String key) {
        Bucket bucket = bridgeBuckets.computeIfAbsent(key, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(bridgeRpm)
                                .refillGreedy(bridgeRpm, Duration.ofMinutes(1))
                                .build())
                        .build());
        if (!bucket.tryConsume(1)) {
            throw new RateLimitException("Too many bridge requests. Limit: " + bridgeRpm + "/min");
        }
    }

    public void checkAuthLimit(String key) {
        Bucket bucket = authBuckets.computeIfAbsent(key, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(authRpm)
                                .refillGreedy(authRpm, Duration.ofMinutes(1))
                                .build())
                        .build());
        if (!bucket.tryConsume(1)) {
            throw new RateLimitException("Too many auth requests. Limit: " + authRpm + "/min");
        }
    }
}
