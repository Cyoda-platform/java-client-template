package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PetController - Manage pet inventory and availability
 * 
 * Base Path: /api/pets
 * Entity: Pet
 * Purpose: Manage pet inventory and availability
 */
@RestController
@RequestMapping("/api/pets")
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
     * Get all pets with optional filtering
     * GET /api/pets
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Pet>>> getAllPets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoryId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            List<EntityWithMetadata<Pet>> allPets;

            if (status != null || categoryId != null) {
                // Build search conditions
                List<QueryCondition> conditions = new ArrayList<>();

                if (categoryId != null) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.categoryId")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(categoryId)));
                }

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);

                allPets = entityService.search(modelSpec, condition, Pet.class);

                // Filter by status if provided (status is in metadata)
                if (status != null) {
                    allPets = allPets.stream()
                            .filter(pet -> status.equals(pet.metadata().getState()))
                            .collect(Collectors.toList());
                }
            } else {
                allPets = entityService.findAll(modelSpec, Pet.class);
            }

            return ResponseEntity.ok(allPets);
        } catch (Exception e) {
            logger.error("Error getting pets", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get pet by technical UUID
     * GET /api/pets/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Pet>> getPetById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> response = entityService.getById(id, modelSpec, Pet.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting pet by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get pet by business identifier
     * GET /api/pets/business/{petId}
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
            logger.error("Error getting pet by business ID: {}", petId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create new pet
     * POST /api/pets
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Pet>> createPet(@RequestBody Pet pet) {
        try {
            // Set creation timestamp
            pet.setCreatedAt(LocalDateTime.now());
            pet.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Pet> response = entityService.create(pet);
            logger.info("Pet created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update pet with optional workflow transition
     * PUT /api/pets/{id}?transitionName=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Pet>> updatePet(
            @PathVariable UUID id,
            @RequestBody Pet pet,
            @RequestParam(required = false) String transitionName) {
        try {
            // Set update timestamp
            pet.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Pet> response = entityService.update(id, pet, transitionName);
            logger.info("Pet updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reserve pet (transition to pending)
     * PUT /api/pets/{petId}/reserve
     */
    @PutMapping("/{petId}/reserve")
    public ResponseEntity<EntityWithMetadata<Pet>> reservePet(
            @PathVariable String petId,
            @RequestBody(required = false) ReservationRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> petEntity = entityService.findByBusinessId(
                    modelSpec, petId, "petId", Pet.class);

            if (petEntity == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Pet> response = entityService.update(
                    petEntity.metadata().getId(), petEntity.entity(), "reserve_pet");
            logger.info("Pet {} reserved", petId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error reserving pet: {}", petId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel pet reservation (transition to available)
     * PUT /api/pets/{petId}/cancel-reservation
     */
    @PutMapping("/{petId}/cancel-reservation")
    public ResponseEntity<EntityWithMetadata<Pet>> cancelReservation(@PathVariable String petId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> petEntity = entityService.findByBusinessId(
                    modelSpec, petId, "petId", Pet.class);

            if (petEntity == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Pet> response = entityService.update(
                    petEntity.metadata().getId(), petEntity.entity(), "cancel_reservation");
            logger.info("Pet {} reservation cancelled", petId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error cancelling pet reservation: {}", petId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete pet
     * DELETE /api/pets/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePet(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Pet deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    /**
     * DTO for pet reservation requests
     */
    @Getter
    @Setter
    public static class ReservationRequest {
        private String customerId;
        private String reservationNotes;
    }
}
