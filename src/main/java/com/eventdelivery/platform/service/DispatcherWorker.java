package com.eventdelivery.platform.service;

import com.eventdelivery.platform.dto.EventMessage;
import com.eventdelivery.platform.model.DeliveryAttempt;
import com.eventdelivery.platform.model.Destination;
import com.eventdelivery.platform.model.Event;
import com.eventdelivery.platform.model.EventStatus;
import com.eventdelivery.platform.repository.DeliveryAttemptRepository;
import com.eventdelivery.platform.repository.DestinationRepository;
import com.eventdelivery.platform.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DispatcherWorker {

    private static final Logger log = LoggerFactory.getLogger(DispatcherWorker.class);

    private final EventRepository eventRepository;
    private final DestinationRepository destinationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RateLimiterService rateLimiterService;

    public DispatcherWorker(EventRepository eventRepository,
            DestinationRepository destinationRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            ObjectMapper objectMapper,
            RateLimiterService rateLimiterService) {
        this.eventRepository = eventRepository;
        this.destinationRepository = destinationRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.objectMapper = objectMapper;
        this.rateLimiterService = rateLimiterService;
        this.restClient = RestClient.create();
    }

    @RetryableTopic(attempts = "5", backoff = @Backoff(delay = 1000, multiplier = 2.0), dltStrategy = DltStrategy.FAIL_ON_ERROR, include = {
            Exception.class })
    @KafkaListener(topics = "events.primary", groupId = "dispatcher-group")
    public void consumeEvent(@Payload String messageJson, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("Consuming event from topic: {}", topic);

        try {
            EventMessage eventMessage = objectMapper.readValue(messageJson, EventMessage.class);
            processEvent(eventMessage);
        } catch (Exception e) {
            log.error("Error processing event", e);
            throw new RuntimeException("Validation or Processing failed", e);
        }
    }

    private void processEvent(EventMessage message) {
        Destination destination = destinationRepository.findById(message.getDestinationId())
                .orElseThrow(() -> new RuntimeException("Destination not found"));

        Event event = eventRepository.findById(message.getEventId())
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (event.getStatus() == EventStatus.RECEIVED || event.getStatus() == EventStatus.FAILED) {
            event.setStatus(EventStatus.PROCESSING);
            eventRepository.save(event);
        }

        long startTime = System.currentTimeMillis();
        boolean success = false;
        int responseCode = 0;
        String responseBody = "";

        try {
            // Rate limiting check - don't want to block the whole consumer thread
            // if we're out of tokens, so we'll throw and let retry handle it
            if (destination.getRateLimitRps() != null && destination.getRateLimitRps() > 0) {
                io.github.bucket4j.Bucket bucket = rateLimiterService.resolveBucket(destination.getId(),
                        destination.getRateLimitRps());
                if (!bucket.tryConsume(1)) {
                    log.warn("Rate limit exceeded for destination {}. Re-queuing event {}", destination.getId(),
                            message.getEventId());
                    // Throwing here triggers retry with backoff
                    // Could use a dedicated delay topic but RetryableTopic works fine
                    throw new RuntimeException("Rate limit exceeded");
                }
            }

            // Sign the payload
            String signature = SignatureUtil.calculateHmac(message.getPayload(), destination.getSigningSecret());

            ResponseEntity<String> response = restClient.post()
                    .uri(destination.getUrl())
                    .header("Content-Type", "application/json")
                    .header("X-Edp-Signature", "sha256=" + signature)
                    .body(message.getPayload())
                    .retrieve()
                    .toEntity(String.class);

            responseCode = response.getStatusCode().value();
            responseBody = response.getBody();
            success = response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            log.error("HTTP Delivery failed: {}", e.getMessage());
            responseCode = 500;
            responseBody = e.getMessage();
            success = false;
        }

        long duration = System.currentTimeMillis() - startTime;

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setEvent(event);
        attempt.setResponseCode(responseCode);
        attempt.setResponseBody(
                responseBody != null ? responseBody.substring(0, Math.min(responseBody.length(), 1000)) : "");
        attempt.setSuccess(success);
        attempt.setDurationMs(duration);

        deliveryAttemptRepository.save(attempt);

        if (success) {
            event.setStatus(EventStatus.DELIVERED);
            eventRepository.save(event);
            log.info("Event {} delivered successfully", event.getId());
        } else {
            event.setStatus(EventStatus.FAILED);
            eventRepository.save(event);
            log.warn("Event {} delivery failed, will retry if eligible", event.getId());
            throw new RuntimeException("Delivery failed");
        }
    }

    @org.springframework.kafka.annotation.DltHandler
    public void dltHandler(String messageJson, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Event moved to DLQ: {}", messageJson);
        try {
            EventMessage eventMessage = objectMapper.readValue(messageJson, EventMessage.class);

            Event event = eventRepository.findById(eventMessage.getEventId()).orElse(null);
            if (event != null) {
                event.setStatus(EventStatus.PERMANENTLY_FAILED);
                eventRepository.save(event);
            }
        } catch (Exception e) {
            log.error("Failed to update status in DLQ handler", e);
        }
    }
}
