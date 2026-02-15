package com.eventdelivery.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class EventRequest {
    @NotNull(message = "Destination ID is required")
    private UUID destinationId;

    @NotBlank(message = "Payload is required")
    private String payload;

    public EventRequest() {
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
}
