package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.CyodaExceptionUtil;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.EntityChangeMeta;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for Pet entity providing CRUD operations, search functionality,
 * and workflow transition endpoints following the thin proxy pattern.
 */
@RestController
@RequestMapping("/ui/pet")
@CrossOrigin(origins = "*")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
                logger.warn("Pet with business ID {} already exists", pet.getPetId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Pet already exists with ID: %s", pet.getPetId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Pet> response = entityService.create(pet);
            logger.info("Pet created with ID: {}", response.metadata().getId());

            // Build Location header for the created resource
            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
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
    public ResponseEntity<EntityWithMetadata<Pet>> getPetById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Pet> response = entityService.getById(id, modelSpec, Pet.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve pet with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get pet by business identifier
     * GET /ui/pet/business/{petId}
     */
    @GetMapping("/business/{petId}")
    public ResponseEntity<EntityWithMetadata<Pet>> getPetByBusinessId(
            @PathVariable String petId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Pet> response = entityService.findByBusinessId(
                    modelSpec, petId, "petId", Pet.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve pet with business ID '%s': %s", petId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update pet with optional workflow transition
     * PUT /ui/pet/{id}
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update pet with ID '%s': %s", id, e.getMessage())
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete pet with ID '%s': %s", id, e.getMessage())
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
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete pet with business ID '%s': %s", petId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * List all pets with pagination and optional filtering
     * GET /ui/pet
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Pet>>> listPets(
            Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            List<QueryCondition> conditions = new ArrayList<>();

            // Add entity field filters
            if (breed != null && !breed.trim().isEmpty()) {
                SimpleCondition breedCondition = new SimpleCondition()
                        .withJsonPath("$.breed")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(breed));
                conditions.add(breedCondition);
            }

            if (conditions.isEmpty() && (status == null || status.trim().isEmpty())) {
                // Use paginated findAll when no filters
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, Pet.class, pointInTimeDate));
            } else {
                // For filtered results, get all matching results then manually paginate
                List<EntityWithMetadata<Pet>> pets;
                if (conditions.isEmpty()) {
                    pets = entityService.findAll(modelSpec, Pet.class, pointInTimeDate);
                } else {
                    GroupCondition groupCondition = new GroupCondition()
                            .withOperator(GroupCondition.Operator.AND)
                            .withConditions(conditions);
                    pets = entityService.search(modelSpec, groupCondition, Pet.class, pointInTimeDate);
                }

                // Filter by status if provided (status is in metadata, not entity)
                if (status != null && !status.trim().isEmpty()) {
                    pets = pets.stream()
                            .filter(pet -> status.equals(pet.metadata().getState()))
                            .toList();
                }

                // Manually paginate the filtered results
                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), pets.size());
                List<EntityWithMetadata<Pet>> pageContent = start < pets.size()
                    ? pets.subList(start, end)
                    : new ArrayList<>();

                Page<EntityWithMetadata<Pet>> page = new PageImpl<>(pageContent, pageable, pets.size());
                return ResponseEntity.ok(page);
            }
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list pets: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
