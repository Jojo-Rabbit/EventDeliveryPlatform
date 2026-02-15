package com.eventdelivery.platform.service;

import com.eventdelivery.platform.dto.DestinationRequest;
import com.eventdelivery.platform.model.Destination;
import com.eventdelivery.platform.repository.DestinationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DestinationService {

    private final DestinationRepository destinationRepository;

    public DestinationService(DestinationRepository destinationRepository) {
        this.destinationRepository = destinationRepository;
    }

    public Destination createDestination(DestinationRequest request) {
        Destination destination = new Destination();
        destination.setName(request.getName());
        destination.setUrl(request.getUrl());
        destination.setHttpMethod(request.getHttpMethod());
        destination.setHeaders(request.getHeaders());

        String secret = request.getSigningSecret();
        if (secret == null || secret.isEmpty()) {
            secret = java.util.UUID.randomUUID().toString();
        }
        destination.setSigningSecret(secret);

        Integer rps = request.getRateLimitRps();
        if (rps == null) {
            rps = 10;
        }
        destination.setRateLimitRps(rps);

        return destinationRepository.save(destination);
    }

    public List<Destination> getAllDestinations() {
        return destinationRepository.findAll();
    }

    public Destination getDestination(UUID id) {
        return destinationRepository.findById(id).orElseThrow();
    }
}
