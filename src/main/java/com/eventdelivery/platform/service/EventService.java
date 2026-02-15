package com.eventdelivery.platform.service;

import com.eventdelivery.platform.dto.EventMessage;
import com.eventdelivery.platform.dto.EventRequest;
import com.eventdelivery.platform.model.Destination;
import com.eventdelivery.platform.model.Event;
import com.eventdelivery.platform.model.EventStatus;
import com.eventdelivery.platform.repository.DestinationRepository;
import com.eventdelivery.platform.repository.EventRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final DestinationRepository destinationRepository;
    private final KafkaProducerService kafkaProducerService;
    private final IdempotencyService idempotencyService;

    public EventService(EventRepository eventRepository, DestinationRepository destinationRepository,
            KafkaProducerService kafkaProducerService, IdempotencyService idempotencyService) {
        this.eventRepository = eventRepository;
        this.destinationRepository = destinationRepository;
        this.kafkaProducerService = kafkaProducerService;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public Event receiveEvent(EventRequest request, String idempotencyKey) {
        Destination destination = destinationRepository.findById(request.getDestinationId())
                .orElseThrow(() -> new IllegalArgumentException("Destination not found"));

        // Check if we've seen this request before
        if (idempotencyKey != null) {
            UUID existingEventId = idempotencyService.getExistingEventId(idempotencyKey, destination.getId());
            if (existingEventId != null) {
                // Already processed this one - grab the original event from DB
                return eventRepository.findById(existingEventId)
                        .orElseThrow(
                                () -> new RuntimeException("Idempotency key found in Redis but event missing in DB"));
            }
        }

        Event event = new Event();
        event.setPayload(request.getPayload());
        event.setDestination(destination);
        event.setStatus(EventStatus.RECEIVED);
        event.setIdempotencyKey(idempotencyKey);

        // Save first so we get an ID
        event = eventRepository.save(event);

        // Now try to claim this idempotency key in Redis
        // Doing this after DB save but if it fails, we'll rollback via exception
        if (idempotencyKey != null) {
            boolean isNew = idempotencyService.process(idempotencyKey, destination.getId(), event.getId());
            if (!isNew) {
                // Someone else got here first - throw to rollback our DB save
                throw new IllegalArgumentException("Duplicate request (Race Condition detected)");
            }
        }

        EventMessage message = new EventMessage(
                event.getId(),
                destination.getId(),
                event.getPayload(),
                0);

        // Send it off to Kafka
        kafkaProducerService.sendEvent(message);

        return event;
    }

    public int replayEvents(com.eventdelivery.platform.dto.ReplayRequest request) {
        log.info("Starting replay for destination: {}", request.getDestinationId());

        // Default to last 24 hours if no start time given
        java.time.LocalDateTime startDate = request.getStartTime() != null ? request.getStartTime()
                : java.time.LocalDateTime.now().minusHours(24);

        List<Event> events = eventRepository.findReplayCandidates(request.getDestinationId(), request.getStatus(),
                startDate);
        log.info("Found {} events to replay", events.size());

        int count = 0;
        for (Event event : events) {
            // Skip if it's already being processed
            if (event.getStatus() != EventStatus.PROCESSING) {
                // Mark as processing again (TODO: maybe add a REPLAYING status?)
                event.setStatus(EventStatus.PROCESSING);
                eventRepository.save(event);

                EventMessage message = new EventMessage(
                        event.getId(),
                        event.getDestination().getId(),
                        event.getPayload(),
                        0 // Start fresh with attempt count
                );
                kafkaProducerService.sendEvent(message);
                count++;
            }
        }
        return count;
    }

    public Event getEvent(UUID id) {
        return eventRepository.findById(id).orElseThrow(() -> new RuntimeException("Event not found"));
    }
}
