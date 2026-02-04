package com.java_template.application.controller;

import com.java_template.application.entity.event.version_1.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * EventController - REST endpoints for event tracking
 */
@RestController
@RequestMapping("/ui/event")
@CrossOrigin(origins = "*")
public class EventController {

    private static final Logger logger = LoggerFactory.getLogger(EventController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EventController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new event (track an event)
     * POST /ui/event
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Event>> createEvent(@RequestBody Event event) {
        try {
            // Check for duplicate event ID
            ModelSpec modelSpec = new ModelSpec().withName(Event.ENTITY_NAME).withVersion(Event.ENTITY_VERSION);
            EntityWithMetadata<Event> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, event.getEventId(), "eventId", Event.class);

            if (existing != null) {
                logger.warn("Event with ID {} already exists", event.getEventId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Event already exists with ID: %s", event.getEventId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Event> response = entityService.create(event);
            logger.info("Event created: {} - {}", event.getEventId(), event.getType());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create event: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get event by ID
     * GET /ui/event/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Event>> getEventById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Event.ENTITY_NAME).withVersion(Event.ENTITY_VERSION);
            EntityWithMetadata<Event> response = entityService.getById(id, modelSpec, Event.class);
            
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve event: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

