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

/**
 * PetController - Manages pet-related operations in the pet store
 * 
 * Base Path: /api/v1/pets
 * Description: REST controller for Pet entity CRUD operations and workflow transitions
 */
@RestController
@RequestMapping("/api/v1/pets")
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
     * POST /api/v1/pets
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
            logger.error("Error creating Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get pet by technical UUID
     * GET /api/v1/pets/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Pet>> getPetById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> response = entityService.getById(id, modelSpec, Pet.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Pet by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get pet by business identifier
     * GET /api/v1/pets/business/{petId}
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
            logger.error("Error getting Pet by business ID: {}", petId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update pet with optional workflow transition
     * PUT /api/v1/pets/{id}?transitionName=TRANSITION_NAME
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
            logger.error("Error updating Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete pet by technical UUID
     * DELETE /api/v1/pets/{id}
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
     * GET /api/v1/pets?status=available&category=Dogs&tags=friendly
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Pet>>> getAllPets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tags) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            
            if (status != null || category != null || tags != null) {
                // Build search conditions
                List<SimpleCondition> conditions = new ArrayList<>();
                
                if (category != null && !category.trim().isEmpty()) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.category.name")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(category)));
                }
                
                if (tags != null && !tags.trim().isEmpty()) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.tags[*].name")
                            .withOperation(Operation.CONTAINS)
                            .withValue(objectMapper.valueToTree(tags)));
                }

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(new ArrayList<QueryCondition>(conditions));

                List<EntityWithMetadata<Pet>> pets = entityService.search(modelSpec, condition, Pet.class);
                
                // Filter by status (entity state) if provided
                if (status != null) {
                    pets = pets.stream()
                            .filter(pet -> status.equals(pet.metadata().getState()))
                            .toList();
                }
                
                return ResponseEntity.ok(pets);
            } else {
                List<EntityWithMetadata<Pet>> pets = entityService.findAll(modelSpec, Pet.class);
                return ResponseEntity.ok(pets);
            }
        } catch (Exception e) {
            logger.error("Error getting all Pets", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search pets by advanced criteria
     * POST /api/v1/pets/search
     */
    @PostMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Pet>>> searchPets(@RequestBody PetSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getName() != null && !searchRequest.getName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getName())));
            }

            if (searchRequest.getBreed() != null && !searchRequest.getBreed().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.breed")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getBreed())));
            }

            if (searchRequest.getMinPrice() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.price")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinPrice())));
            }

            if (searchRequest.getMaxPrice() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.price")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMaxPrice())));
            }

            if (searchRequest.getVaccinated() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.vaccinated")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getVaccinated())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<QueryCondition>(conditions));

            List<EntityWithMetadata<Pet>> pets = entityService.search(modelSpec, condition, Pet.class);
            return ResponseEntity.ok(pets);
        } catch (Exception e) {
            logger.error("Error performing pet search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for pet search requests
     */
    @Getter
    @Setter
    public static class PetSearchRequest {
        private String name;
        private String breed;
        private Double minPrice;
        private Double maxPrice;
        private Boolean vaccinated;
    }
}
