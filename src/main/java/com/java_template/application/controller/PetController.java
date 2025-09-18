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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PetController - REST endpoints for pet operations
 * 
 * Manages pet search, reservation, adoption, and return operations
 * through thin proxy methods to EntityService.
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
     * Create a new pet
     * POST /api/pets
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Pet>> createPet(@RequestBody Pet pet) {
        try {
            // Set arrival date if not provided
            if (pet.getArrivalDate() == null) {
                pet.setArrivalDate(LocalDate.now());
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
            logger.error("Error getting Pet by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Search and list pets with filtering options
     * GET /api/pets?species=dog&state=available
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Pet>>> searchPets(
            @RequestParam(required = false) String species,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String state) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (species != null && !species.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.species")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(species)));
            }

            if (breed != null && !breed.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.breed")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(breed)));
            }

            List<EntityWithMetadata<Pet>> pets;
            if (conditions.isEmpty()) {
                pets = entityService.findAll(modelSpec, Pet.class);
            } else {
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                pets = entityService.search(modelSpec, condition, Pet.class);
            }

            // Filter by state if provided (state is in metadata, not entity)
            if (state != null && !state.trim().isEmpty()) {
                pets = pets.stream()
                        .filter(pet -> state.equals(pet.metadata().getState()))
                        .toList();
            }

            return ResponseEntity.ok(pets);
        } catch (Exception e) {
            logger.error("Error searching pets", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reserve a pet for adoption
     * POST /api/pets/{id}/reserve
     */
    @PostMapping("/{id}/reserve")
    public ResponseEntity<EntityWithMetadata<Pet>> reservePet(
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest request) {
        try {
            String transition = (request != null && request.getTransitionName() != null) 
                ? request.getTransitionName() : "reserve_pet";
            
            // Get current pet to update
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> currentPet = entityService.getById(id, modelSpec, Pet.class);
            
            EntityWithMetadata<Pet> response = entityService.update(id, currentPet.entity(), transition);
            logger.info("Pet reserved with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error reserving Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Complete pet adoption
     * POST /api/pets/{id}/adopt
     */
    @PostMapping("/{id}/adopt")
    public ResponseEntity<EntityWithMetadata<Pet>> adoptPet(
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest request) {
        try {
            String transition = (request != null && request.getTransitionName() != null) 
                ? request.getTransitionName() : "adopt_pet";
            
            // Get current pet to update
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> currentPet = entityService.getById(id, modelSpec, Pet.class);
            
            EntityWithMetadata<Pet> response = entityService.update(id, currentPet.entity(), transition);
            logger.info("Pet adopted with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error adopting Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Return an adopted pet
     * POST /api/pets/{id}/return
     */
    @PostMapping("/{id}/return")
    public ResponseEntity<EntityWithMetadata<Pet>> returnPet(
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest request) {
        try {
            String transition = (request != null && request.getTransitionName() != null) 
                ? request.getTransitionName() : "return_pet";
            
            // Get current pet to update
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> currentPet = entityService.getById(id, modelSpec, Pet.class);
            
            EntityWithMetadata<Pet> response = entityService.update(id, currentPet.entity(), transition);
            logger.info("Pet returned with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error returning Pet", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel pet reservation
     * POST /api/pets/{id}/cancel-reservation
     */
    @PostMapping("/{id}/cancel-reservation")
    public ResponseEntity<EntityWithMetadata<Pet>> cancelReservation(
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest request) {
        try {
            String transition = (request != null && request.getTransitionName() != null) 
                ? request.getTransitionName() : "cancel_reservation";
            
            // Get current pet to update
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> currentPet = entityService.getById(id, modelSpec, Pet.class);
            
            EntityWithMetadata<Pet> response = entityService.update(id, currentPet.entity(), transition);
            logger.info("Pet reservation cancelled with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error cancelling Pet reservation", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Make returned pet available again
     * POST /api/pets/{id}/make-available
     */
    @PostMapping("/{id}/make-available")
    public ResponseEntity<EntityWithMetadata<Pet>> makePetAvailable(
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest request) {
        try {
            String transition = (request != null && request.getTransitionName() != null) 
                ? request.getTransitionName() : "make_available_again";
            
            // Get current pet to update
            ModelSpec modelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> currentPet = entityService.getById(id, modelSpec, Pet.class);
            
            EntityWithMetadata<Pet> response = entityService.update(id, currentPet.entity(), transition);
            logger.info("Pet made available again with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error making Pet available again", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for transition requests
     */
    @Getter
    @Setter
    public static class TransitionRequest {
        private String transitionName;
        private String details;
    }
}
