package com.java_template.application.controller;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for managing Pet entities and loading pet data from external APIs.
 * Provides CRUD operations and pet-specific endpoints.
 */
@RestController
@RequestMapping("/ui/pet")
@CrossOrigin(origins = "*")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);
    private final EntityService entityService;

    public PetController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create a new pet
     * POST /ui/pet
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Pet>> createPet(@Valid @RequestBody Pet pet) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, pet.getPetId(), "petId", Pet.class);

            if (existing != null) {
                logger.warn("Pet with ID {} already exists", pet.getPetId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Pet already exists with ID: %s", pet.getPetId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Pet> response = entityService.create(pet);
            logger.info("Pet created with technical ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create pet", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create pet: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get pet by technical UUID
     * GET /ui/pet/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Pet>> getPetById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> response = entityService.getById(id, modelSpec, Pet.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve pet with ID {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve pet: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get pet by business identifier
     * GET /ui/pet/business/{petId}
     */
    @GetMapping("/business/{petId}")
    public ResponseEntity<EntityWithMetadata<Pet>> getPetByBusinessId(@PathVariable String petId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> response = entityService.findByBusinessId(
                    modelSpec, petId, "petId", Pet.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve pet with business ID {}", petId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve pet: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update pet with optional workflow transition
     * PUT /ui/pet/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Pet>> updatePet(
            @PathVariable UUID id,
            @Valid @RequestBody Pet pet,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Pet> response = entityService.update(id, pet, transition);
            logger.info("Pet updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update pet with ID {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update pet: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * List all pets with pagination
     * GET /ui/pet?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Pet>>> listPets(Pageable pageable) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            Page<EntityWithMetadata<Pet>> response = entityService.findAll(modelSpec, pageable, Pet.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to list pets", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list pets: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete pet by technical UUID
     * DELETE /ui/pet/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePet(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Pet deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete pet with ID {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete pet: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete pet by business identifier
     * DELETE /ui/pet/business/{petId}
     */
    @DeleteMapping("/business/{petId}")
    public ResponseEntity<Void> deletePetByBusinessId(@PathVariable String petId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            boolean deleted = entityService.deleteByBusinessId(modelSpec, petId, "petId", Pet.class);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Pet deleted with business ID: {}", petId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete pet with business ID {}", petId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete pet: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

