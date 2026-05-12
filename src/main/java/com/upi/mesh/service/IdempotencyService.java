package com.upi.mesh.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production idempotency:
 *   PRIMARY  — Redis SETNX with TTL (distributed, survives restarts)
 *   FALLBACK — In-memory ConcurrentHashMap (when Redis is unavailable)
 *
 * Redis SETNX is atomic by nature — safe across multiple server instances.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "upi:idempotency:";

    @Autowired(required = false)
    private StringRedisTemplate redis;

    @Value("${upi.mesh.idempotency-ttl-seconds:86400}")
    private long ttlSeconds;

    // Fallback in-memory map (used when Redis is down)
    private final Map<String, Instant> fallback = new ConcurrentHashMap<>();
    private volatile boolean redisAvailable = true;

    /**
     * Returns true if this caller is the FIRST to claim this hash (process it).
     * Returns false = duplicate, should be dropped.
     */
    public boolean claim(String packetHash) {
        // Try Redis first
        if (redis != null && redisAvailable) {
            try {
                Boolean set = redis.opsForValue().setIfAbsent(
                        KEY_PREFIX + packetHash,
                        "1",
                        Duration.ofSeconds(ttlSeconds)
                );
                return Boolean.TRUE.equals(set);
            } catch (Exception e) {
                log.warn("Redis unavailable, falling back to in-memory idempotency: {}", e.getMessage());
                redisAvailable = false;
            }
        }

        // Fallback: in-memory
        Instant prev = fallback.putIfAbsent(packetHash, Instant.now());
        return prev == null;
    }

    public int size() {
        if (redis != null && redisAvailable) {
            try {
                int size = redis.keys(KEY_PREFIX + "*").size();
                return size;
            } catch (Exception e) {
                return fallback.size();
            }
        }
        return fallback.size();
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictExpiredFallback() {
        Instant cutoff = Instant.now().minusSeconds(ttlSeconds);
        fallback.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));

        // Retry Redis if it was down
        if (!redisAvailable && redis != null) {
            try {
                redis.opsForValue().get("upi:ping");
                redisAvailable = true;
                log.info("Redis reconnected, switching back to Redis idempotency");
            } catch (Exception ignored) {}
        }
    }

    public void clear() {
        fallback.clear();
        if (redis != null && redisAvailable) {
            try {
                var keys = redis.keys(KEY_PREFIX + "*");
                if (keys != null && !keys.isEmpty()) redis.delete(keys);
            } catch (Exception ignored) {}
        }
    }
}
