package com.eventdelivery.platform.service;

import com.eventdelivery.platform.dto.EventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    private static final String TOPIC = "events.primary";

    public void sendEvent(EventMessage eventMessage) {
        try {
            String message = objectMapper.writeValueAsString(eventMessage);
            kafkaTemplate.send(TOPIC, eventMessage.getEventId().toString(), message)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Sent event {} to topic {}", eventMessage.getEventId(), TOPIC);
                        } else {
                            log.error("Failed to send event {} to topic {}", eventMessage.getEventId(), TOPIC, ex);
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Error serializing event message", e);
            throw new RuntimeException("Error serializing event message", e);
        }
    }
}
