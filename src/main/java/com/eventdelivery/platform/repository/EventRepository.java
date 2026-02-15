package com.eventdelivery.platform.repository;

import com.eventdelivery.platform.model.Event;
import com.eventdelivery.platform.model.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByStatus(EventStatus status);

    Optional<Event> findByIdempotencyKeyAndDestinationId(String idempotencyKey, UUID destinationId);

    // Basic replay query - could use Specifications for more complex filters
    @Query("SELECT e FROM Event e WHERE e.destination.id = :destinationId AND (:status IS NULL OR e.status = :status) AND e.createdAt >= :startDate")
    List<Event> findReplayCandidates(UUID destinationId, EventStatus status, LocalDateTime startDate);
}
