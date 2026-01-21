package com.java_template.application.controller;

import com.java_template.application.entity.manualusersync.version_1.ManualUserSync;
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
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ManualUserSyncController - REST API for manual user synchronization
 * 
 * Provides endpoints for triggering and managing Auth0 user sync operations.
 */
@RestController
@RequestMapping("/ui/manualusersync")
@CrossOrigin(origins = "*")
public class ManualUserSyncController {

    private static final Logger logger = LoggerFactory.getLogger(ManualUserSyncController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ManualUserSyncController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<ManualUserSync>> createSync(@RequestBody ManualUserSync syncEntity) {
        try {
            // Initialize sync entity with defaults
            if (syncEntity.getCreatedAt() == null) {
                syncEntity.setCreatedAt(OffsetDateTime.now());
            }
            if (syncEntity.getUpdatedAt() == null) {
                syncEntity.setUpdatedAt(OffsetDateTime.now());
            }
            if (syncEntity.getStatus() == null) {
                syncEntity.setStatus("idle");
            }

            EntityWithMetadata<ManualUserSync> response = entityService.create(syncEntity);
            logger.info("ManualUserSync created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create sync: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<ManualUserSync>> getSyncById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(ManualUserSync.ENTITY_NAME)
                .withVersion(ManualUserSync.ENTITY_VERSION);
            EntityWithMetadata<ManualUserSync> response = entityService.getById(id, modelSpec, ManualUserSync.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve sync: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PostMapping("/{id}/start-sync")
    public ResponseEntity<EntityWithMetadata<ManualUserSync>> startSync(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(ManualUserSync.ENTITY_NAME)
                .withVersion(ManualUserSync.ENTITY_VERSION);
            EntityWithMetadata<ManualUserSync> current = entityService.getById(id, modelSpec, ManualUserSync.class);

            EntityWithMetadata<ManualUserSync> response = entityService.update(id, current.entity(), "start_sync");
            logger.info("ManualUserSync started with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to start sync: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSync(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("ManualUserSync deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete sync: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

