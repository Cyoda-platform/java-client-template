package com.java_template.application.controller;

import com.java_template.application.entity.environment.version_1.Environment;
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
 * EnvironmentController - REST API for Environment management
 * 
 * Provides endpoints for managing user environments and access tracking.
 */
@RestController
@RequestMapping("/ui/environment")
@CrossOrigin(origins = "*")
public class EnvironmentController {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EnvironmentController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Environment>> createEnvironment(@RequestBody Environment environment) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Environment.ENTITY_NAME).withVersion(Environment.ENTITY_VERSION);
            EntityWithMetadata<Environment> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, environment.getEnvironmentId(), "environmentId", Environment.class);

            if (existing != null) {
                logger.warn("Environment with ID {} already exists", environment.getEnvironmentId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Environment already exists with ID: %s", environment.getEnvironmentId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Environment> response = entityService.create(environment);
            logger.info("Environment created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create environment: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Environment>> getEnvironmentById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Environment.ENTITY_NAME).withVersion(Environment.ENTITY_VERSION);
            EntityWithMetadata<Environment> response = entityService.getById(id, modelSpec, Environment.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve environment: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Environment>> updateEnvironment(
            @PathVariable UUID id,
            @RequestBody Environment environment,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Environment> response = entityService.update(id, environment, transition);
            logger.info("Environment updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update environment: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEnvironment(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Environment deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete environment: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

