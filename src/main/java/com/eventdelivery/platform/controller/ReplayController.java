package com.eventdelivery.platform.controller;

import com.eventdelivery.platform.dto.ReplayRequest;
import com.eventdelivery.platform.service.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/replays")
public class ReplayController {

    private final EventService eventService;

    public ReplayController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> replayEvents(@RequestBody ReplayRequest request) {
        int count = eventService.replayEvents(request);
        return ResponseEntity.ok(Map.of("message", "Triggered replay for " + count + " events"));
    }
}
