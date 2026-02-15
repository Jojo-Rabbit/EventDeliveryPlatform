package com.eventdelivery.platform.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterService {

    // Using in-memory for now - TODO: move to Redis for prod
    private final Map<UUID, Bucket> cache = new ConcurrentHashMap<>();

    public Bucket resolveBucket(UUID destinationId, int rps) {
        return cache.computeIfAbsent(destinationId, id -> newBucket(rps));
    }

    private Bucket newBucket(int rps) {
        Refill refill = Refill.intervally(rps, Duration.ofSeconds(1));
        Bandwidth limit = Bandwidth.classic(rps, refill);
        return Bucket.builder().addLimit(limit).build();
    }
}
