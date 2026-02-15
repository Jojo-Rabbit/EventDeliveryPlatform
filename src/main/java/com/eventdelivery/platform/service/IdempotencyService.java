package com.eventdelivery.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private final StringRedisTemplate redisTemplate;

    // Keep keys around for 24 hours
    private static final Duration KEY_TTL = Duration.ofHours(24);

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Tries to acquire a lock for the given idempotency key and destination.
     * 
     * @return true if the key is new (lock acquired), false if it already exists.
     */
    public boolean process(String idempotencyKey, UUID destinationId, UUID eventId) {
        if (idempotencyKey == null) {
            return true; // No key? Just process it
        }

        String redisKey = "idemp:" + destinationId + ":" + idempotencyKey;

        // setIfAbsent is atomic (Redis SETNX)
        Boolean success = redisTemplate.opsForValue().setIfAbsent(redisKey, eventId.toString(), KEY_TTL);

        if (Boolean.TRUE.equals(success)) {
            return true;
        } else {
            String existingEventId = redisTemplate.opsForValue().get(redisKey);
            log.info("Duplicate request detected for key {}. Existing event ID: {}", redisKey, existingEventId);
            return false;
        }
    }

    public UUID getExistingEventId(String idempotencyKey, UUID destinationId) {
        String redisKey = "idemp:" + destinationId + ":" + idempotencyKey;
        String eventIdStr = redisTemplate.opsForValue().get(redisKey);
        return eventIdStr != null ? UUID.fromString(eventIdStr) : null;
    }
}
