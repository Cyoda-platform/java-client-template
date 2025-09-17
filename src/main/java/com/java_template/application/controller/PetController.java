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
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PetController - REST API endpoints for managing pets in the Purrfect Pets system
 * 
 * Purpose: Provides CRUD operations and workflow transitions for Pet entities
 * Endpoints: POST /pets, GET /pets/{id}, PUT /pets/{id}, GET /pets
 */
@RestController
@RequestMapping("/pets")
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
     * POST /pets
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Pet>> createPet(@RequestBody Pet pet) {
        try {
            // Set arrival date if not provided
            if (pet.getArrivalDate() == null) {
                pet.setArrivalDate(LocalDateTime.now());
            }

            // Create pet entity
            EntityWithMetadata<Pet> response = entityService.create(pet);
            logger.info("Pet created with ID: {} and petId: {}", 
                       response.metadata().getId(), pet.getPetId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get pet by technical UUID
     * GET /pets/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Pet>> getPetById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Pet.ENTITY_NAME)
                    .withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> response = entityService.getById(id, modelSpec, Pet.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Pet by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get pet by business identifier (petId)
     * GET /pets/business/{petId}
     */
    @GetMapping("/business/{petId}")
    public ResponseEntity<EntityWithMetadata<Pet>> getPetByBusinessId(@PathVariable String petId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Pet.ENTITY_NAME)
                    .withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> response = entityService.findByBusinessId(
                    modelSpec, petId, "petId", Pet.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Pet by business ID: {}", petId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update pet with optional workflow transition
     * PUT /pets/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Pet>> updatePet(
            @PathVariable UUID id,
            @RequestBody UpdatePetRequest request) {
        try {
            Pet pet = request.getEntity();
            String transition = request.getTransition();

            // Update pet entity with optional transition
            EntityWithMetadata<Pet> response = entityService.update(id, pet, transition);
            logger.info("Pet updated with ID: {} and transition: {}", id, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete pet by technical UUID
     * DELETE /pets/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePet(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Pet deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all pets with optional filtering
     * GET /pets?species=dog&size=large&healthStatus=healthy
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Pet>>> getAllPets(
            @RequestParam(required = false) String species,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String healthStatus) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Pet.ENTITY_NAME)
                    .withVersion(Pet.ENTITY_VERSION);

            // If no filters, return all pets
            if (species == null && size == null && healthStatus == null) {
                List<EntityWithMetadata<Pet>> pets = entityService.findAll(modelSpec, Pet.class);
                return ResponseEntity.ok(pets);
            }

            // Build search conditions for filtering
            List<SimpleCondition> conditions = new ArrayList<>();

            if (species != null && !species.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.species")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(species)));
            }

            if (size != null && !size.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.size")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(size)));
            }

            if (healthStatus != null && !healthStatus.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.healthStatus")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(healthStatus)));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Pet>> pets = entityService.search(modelSpec, condition, Pet.class);
            return ResponseEntity.ok(pets);
        } catch (Exception e) {
            logger.error("Error getting filtered Pets", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search pets by name
     * GET /pets/search?name=buddy
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Pet>>> searchPetsByName(
            @RequestParam String name) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Pet.ENTITY_NAME)
                    .withVersion(Pet.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.name")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(name));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Pet>> pets = entityService.search(modelSpec, condition, Pet.class);
            return ResponseEntity.ok(pets);
        } catch (Exception e) {
            logger.error("Error searching Pets by name: {}", name, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    /**
     * DTO for update requests with optional transition
     */
    @Getter
    @Setter
    public static class UpdatePetRequest {
        private Pet entity;
        private String transition;
    }
}
