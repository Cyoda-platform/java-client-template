package com.java_template.application.controller;

import com.java_template.application.entity.catfact.version_1.CatFact;
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
 * CatFactController - REST endpoints for cat fact management
 */
@RestController
@RequestMapping("/ui/catfact")
@CrossOrigin(origins = "*")
public class CatFactController {

    private static final Logger logger = LoggerFactory.getLogger(CatFactController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CatFactController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new cat fact
     * POST /ui/catfact
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<CatFact>> createCatFact(@RequestBody CatFact catFact) {
        try {
            // Check for duplicate fact ID
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            EntityWithMetadata<CatFact> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, catFact.getFactId(), "factId", CatFact.class);

            if (existing != null) {
                logger.warn("CatFact with ID {} already exists", catFact.getFactId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("CatFact already exists with ID: %s", catFact.getFactId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<CatFact> response = entityService.create(catFact);
            logger.info("CatFact created with ID: {}", catFact.getFactId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create cat fact: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get cat fact by ID
     * GET /ui/catfact/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<CatFact>> getCatFactById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            EntityWithMetadata<CatFact> response = entityService.getById(id, modelSpec, CatFact.class);
            
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve cat fact: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Send campaign (transition to sent state)
     * POST /ui/catfact/{id}/send-campaign
     */
    @PostMapping("/{id}/send-campaign")
    public ResponseEntity<EntityWithMetadata<CatFact>> sendCampaign(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CatFact.ENTITY_NAME).withVersion(CatFact.ENTITY_VERSION);
            EntityWithMetadata<CatFact> current = entityService.getById(id, modelSpec, CatFact.class);
            
            EntityWithMetadata<CatFact> response = entityService.update(id, current.entity(), "SEND_CAMPAIGN");
            logger.info("CatFact campaign sent: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to send campaign: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

