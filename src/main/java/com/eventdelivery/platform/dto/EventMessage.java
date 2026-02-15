package com.eventdelivery.platform.dto;

import java.util.UUID;

public class EventMessage {
    private UUID eventId;
    private UUID destinationId;
    private String payload;
    private int attemptCount;

    public EventMessage() {
    }

    public EventMessage(UUID eventId, UUID destinationId, String payload, int attemptCount) {
        this.eventId = eventId;
        this.destinationId = destinationId;
        this.payload = payload;
        this.attemptCount = attemptCount;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public UUID getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(UUID destinationId) {
        this.destinationId = destinationId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }
}
