package com.eventdelivery.platform.controller;

import com.eventdelivery.platform.dto.EventRequest;
import com.eventdelivery.platform.model.Event;
import com.eventdelivery.platform.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<Event> createEvent(
            @Valid @RequestBody EventRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        Event createdEvent = eventService.receiveEvent(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(createdEvent);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Event> getEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.getEvent(id));
    }
}
