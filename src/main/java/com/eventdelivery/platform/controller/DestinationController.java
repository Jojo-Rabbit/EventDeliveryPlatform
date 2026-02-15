package com.eventdelivery.platform.controller;

import com.eventdelivery.platform.dto.DestinationRequest;
import com.eventdelivery.platform.model.Destination;
import com.eventdelivery.platform.service.DestinationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/destinations")
public class DestinationController {

    private final DestinationService destinationService;

    public DestinationController(DestinationService destinationService) {
        this.destinationService = destinationService;
    }

    @PostMapping
    public ResponseEntity<Destination> createDestination(@Valid @RequestBody DestinationRequest request) {
        return ResponseEntity.ok(destinationService.createDestination(request));
    }

    @GetMapping
    public ResponseEntity<List<Destination>> getAllDestinations() {
        return ResponseEntity.ok(destinationService.getAllDestinations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Destination> getDestination(@PathVariable UUID id) {
        return ResponseEntity.ok(destinationService.getDestination(id));
    }
}
