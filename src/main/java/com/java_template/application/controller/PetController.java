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
 * PetController - Manages pet entities and their workflow transitions
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

            EntityWithMetadata<Pet> response = entityService.create(pet);
            logger.info("Pet created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update pet with optional transition
     * PUT /pets/{uuid}
     */
    @PutMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<Pet>> updatePet(
            @PathVariable UUID uuid,
            @RequestBody Pet pet,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Pet> response = entityService.update(uuid, pet, transition);
            logger.info("Pet updated with ID: {}", uuid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get pet by UUID
     * GET /pets/{uuid}
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<Pet>> getPetById(@PathVariable UUID uuid) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> response = entityService.getById(uuid, modelSpec, Pet.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Pet by ID: {}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get pet by business identifier
     * GET /pets/business/{petId}
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
     * List all pets with optional filters
     * GET /pets
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
     * Execute specific transition
     * POST /pets/{uuid}/transitions/{transitionName}
     */
    @PostMapping("/{uuid}/transitions/{transitionName}")
    public ResponseEntity<EntityWithMetadata<Pet>> executeTransition(
            @PathVariable UUID uuid,
            @PathVariable String transitionName,
            @RequestBody(required = false) Pet pet) {
        try {
            // If pet data is provided, use it for the update, otherwise get current pet
            Pet petToUpdate = pet;
            if (petToUpdate == null) {
                ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
                EntityWithMetadata<Pet> currentPet = entityService.getById(uuid, modelSpec, Pet.class);
                petToUpdate = currentPet.entity();
            }

            EntityWithMetadata<Pet> response = entityService.update(uuid, petToUpdate, transitionName);
            logger.info("Pet transition {} executed for ID: {}", transitionName, uuid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error executing transition {} for Pet ID: {}", transitionName, uuid, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search pets by species
     * GET /pets/search/species?species=dog
     */
    @GetMapping("/search/species")
    public ResponseEntity<List<EntityWithMetadata<Pet>>> searchPetsBySpecies(@RequestParam String species) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.species")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(species));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Pet>> pets = entityService.search(modelSpec, condition, Pet.class);
            return ResponseEntity.ok(pets);
        } catch (Exception e) {
            logger.error("Error searching Pets by species: {}", species, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for pets
     * POST /pets/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Pet>>> advancedSearch(@RequestBody PetSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getSpecies() != null && !searchRequest.getSpecies().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.species")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getSpecies())));
            }

            if (searchRequest.getSize() != null && !searchRequest.getSize().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.size")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getSize())));
            }

            if (searchRequest.getMaxAge() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.age")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMaxAge())));
            }

            if (searchRequest.getVaccinated() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.vaccinated")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getVaccinated())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<Pet>> pets = entityService.search(modelSpec, condition, Pet.class);
            return ResponseEntity.ok(pets);
        } catch (Exception e) {
            logger.error("Error performing advanced pet search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete pet by UUID
     * DELETE /pets/{uuid}
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> deletePet(@PathVariable UUID uuid) {
        try {
            entityService.deleteById(uuid);
            logger.info("Pet deleted with ID: {}", uuid);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced pet search requests
     */
    @Getter
    @Setter
    public static class PetSearchRequest {
        private String species;
        private String size;
        private Integer maxAge;
        private Boolean vaccinated;
    }
}
