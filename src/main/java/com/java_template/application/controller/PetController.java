package com.java_template.application.controller;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PetController - REST API controller for Pet entity management
 * 
 * Provides CRUD operations and workflow state transitions for pets
 * in the Purrfect Pets API system.
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
     * Triggers register_pet transition (none → REGISTERED)
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Pet>> createPet(@RequestBody Pet pet) {
        try {
            // Set registration timestamp
            pet.setRegistrationDate(LocalDateTime.now());
            
            // Create pet entity - this will trigger the register_pet transition
            EntityWithMetadata<Pet> response = entityService.create(pet);
            logger.info("Pet created with ID: {} and petId: {}", response.getId(), pet.getPetId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get pet by technical UUID (FASTEST method)
     * GET /ui/pet/{id}
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
     * Get pet by business identifier (petId)
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
            logger.error("Error getting Pet by business ID: {}", petId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all pets owned by a specific owner
     * GET /ui/pet/owner/{ownerId}
     */
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<EntityWithMetadata<Pet>>> getPetsByOwner(@PathVariable String ownerId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            
            // Create search condition for ownerId
            SearchConditionRequest searchRequest = SearchConditionRequest.group("AND",
                Condition.of("$.ownerId", "EQUALS", ownerId)
            );

            List<EntityWithMetadata<Pet>> pets = entityService.search(modelSpec, searchRequest, Pet.class);
            return ResponseEntity.ok(pets);
        } catch (Exception e) {
            logger.error("Error getting Pets by owner: {}", ownerId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update pet with optional workflow transition
     * PUT /ui/pet/{id}?transition={transitionName}
     * 
     * Supported transitions:
     * - activate_pet (REGISTERED → ACTIVE)
     * - deactivate_pet (ACTIVE → INACTIVE)
     * - reactivate_pet (INACTIVE → ACTIVE)
     * - archive_pet (ACTIVE/INACTIVE → ARCHIVED)
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Pet>> updatePet(
            @PathVariable UUID id,
            @RequestBody Pet pet,
            @RequestParam(required = false) String transition) {
        try {
            // Update pet entity with optional transition
            EntityWithMetadata<Pet> response = entityService.update(id, pet, transition);
            logger.info("Pet updated with ID: {} and transition: {}", id, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Pet with ID: {} and transition: {}", id, transition, e);
            return ResponseEntity.badRequest().build();
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
            logger.error("Error deleting Pet with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all pets (USE SPARINGLY - can be slow for large datasets)
     * GET /ui/pet
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Pet>>> getAllPets() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            List<EntityWithMetadata<Pet>> pets = entityService.findAll(modelSpec, Pet.class);
            return ResponseEntity.ok(pets);
        } catch (Exception e) {
            logger.error("Error getting all Pets", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search pets by species
     * GET /ui/pet/search?species=Dog
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Pet>>> searchPetsBySpecies(
            @RequestParam String species) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            
            // Create search condition
            SearchConditionRequest searchRequest = SearchConditionRequest.group("AND",
                Condition.of("$.species", "EQUALS", species)
            );

            List<EntityWithMetadata<Pet>> pets = entityService.search(modelSpec, searchRequest, Pet.class);
            return ResponseEntity.ok(pets);
        } catch (Exception e) {
            logger.error("Error searching Pets by species: {}", species, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for pets
     * POST /ui/pet/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Pet>>> advancedSearch(
            @RequestBody PetSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            
            // Build complex search condition
            List<Condition> conditions = new ArrayList<>();

            if (searchRequest.getSpecies() != null && !searchRequest.getSpecies().trim().isEmpty()) {
                conditions.add(Condition.of("$.species", "EQUALS", searchRequest.getSpecies()));
            }

            if (searchRequest.getBreed() != null && !searchRequest.getBreed().trim().isEmpty()) {
                conditions.add(Condition.of("$.breed", "CONTAINS", searchRequest.getBreed()));
            }

            if (searchRequest.getMinAge() != null) {
                conditions.add(Condition.of("$.age", "GREATER_THAN_OR_EQUAL", searchRequest.getMinAge()));
            }

            if (searchRequest.getMaxAge() != null) {
                conditions.add(Condition.of("$.age", "LESS_THAN_OR_EQUAL", searchRequest.getMaxAge()));
            }

            if (searchRequest.getOwnerId() != null && !searchRequest.getOwnerId().trim().isEmpty()) {
                conditions.add(Condition.of("$.ownerId", "EQUALS", searchRequest.getOwnerId()));
            }

            SearchConditionRequest searchConditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
            List<EntityWithMetadata<Pet>> pets = entityService.search(modelSpec, searchConditionRequest, Pet.class);
            return ResponseEntity.ok(pets);
        } catch (Exception e) {
            logger.error("Error performing advanced pet search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced search requests
     */
    public static class PetSearchRequest {
        private String species;
        private String breed;
        private Integer minAge;
        private Integer maxAge;
        private String ownerId;

        // Getters and setters
        public String getSpecies() { return species; }
        public void setSpecies(String species) { this.species = species; }
        
        public String getBreed() { return breed; }
        public void setBreed(String breed) { this.breed = breed; }
        
        public Integer getMinAge() { return minAge; }
        public void setMinAge(Integer minAge) { this.minAge = minAge; }
        
        public Integer getMaxAge() { return maxAge; }
        public void setMaxAge(Integer maxAge) { this.maxAge = maxAge; }
        
        public String getOwnerId() { return ownerId; }
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    }
}
