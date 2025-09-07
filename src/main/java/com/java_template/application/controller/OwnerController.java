package com.java_template.application.controller;

import com.java_template.application.entity.owner.version_1.Owner;
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
 * OwnerController - REST API controller for Owner entity management
 * 
 * Provides CRUD operations and workflow state transitions for owners
 * in the Purrfect Pets API system.
 */
@RestController
@RequestMapping("/ui/owner")
@CrossOrigin(origins = "*")
public class OwnerController {

    private static final Logger logger = LoggerFactory.getLogger(OwnerController.class);
    private final EntityService entityService;

    public OwnerController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create a new owner
     * POST /ui/owner
     * Triggers register_owner transition (none → PENDING)
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Owner>> createOwner(@RequestBody Owner owner) {
        try {
            // Set registration timestamp and initialize pet count
            owner.setRegistrationDate(LocalDateTime.now());
            owner.setTotalPets(0);
            
            // Create owner entity - this will trigger the register_owner transition
            EntityWithMetadata<Owner> response = entityService.create(owner);
            logger.info("Owner created with ID: {} and ownerId: {}", response.getId(), owner.getOwnerId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating Owner", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get owner by technical UUID (FASTEST method)
     * GET /ui/owner/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Owner>> getOwnerById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> response = entityService.getById(id, modelSpec, Owner.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Owner by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get owner by business identifier (ownerId)
     * GET /ui/owner/business/{ownerId}
     */
    @GetMapping("/business/{ownerId}")
    public ResponseEntity<EntityWithMetadata<Owner>> getOwnerByBusinessId(@PathVariable String ownerId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> response = entityService.findByBusinessId(
                modelSpec, ownerId, "ownerId", Owner.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting Owner by business ID: {}", ownerId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update owner with optional workflow transition
     * PUT /ui/owner/{id}?transition={transitionName}
     * 
     * Supported transitions:
     * - verify_owner (PENDING → ACTIVE)
     * - suspend_owner (ACTIVE → SUSPENDED)
     * - reactivate_owner (SUSPENDED → ACTIVE)
     * - close_owner_account (ACTIVE/SUSPENDED → CLOSED)
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Owner>> updateOwner(
            @PathVariable UUID id,
            @RequestBody Owner owner,
            @RequestParam(required = false) String transition) {
        try {
            // Update owner entity with optional transition
            EntityWithMetadata<Owner> response = entityService.update(id, owner, transition);
            logger.info("Owner updated with ID: {} and transition: {}", id, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating Owner with ID: {} and transition: {}", id, transition, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete owner by technical UUID
     * DELETE /ui/owner/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOwner(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Owner deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting Owner with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all owners (USE SPARINGLY - can be slow for large datasets)
     * GET /ui/owner
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Owner>>> getAllOwners() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            List<EntityWithMetadata<Owner>> owners = entityService.findAll(modelSpec, Owner.class);
            return ResponseEntity.ok(owners);
        } catch (Exception e) {
            logger.error("Error getting all Owners", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search owners by email
     * GET /ui/owner/search?email=john@example.com
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Owner>>> searchOwnersByEmail(
            @RequestParam String email) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            
            // Create search condition
            SearchConditionRequest searchRequest = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EQUALS", email)
            );

            List<EntityWithMetadata<Owner>> owners = entityService.search(modelSpec, searchRequest, Owner.class);
            return ResponseEntity.ok(owners);
        } catch (Exception e) {
            logger.error("Error searching Owners by email: {}", email, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for owners
     * POST /ui/owner/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<Owner>>> advancedSearch(
            @RequestBody OwnerSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            
            // Build complex search condition
            List<Condition> conditions = new ArrayList<>();

            if (searchRequest.getFirstName() != null && !searchRequest.getFirstName().trim().isEmpty()) {
                conditions.add(Condition.of("$.firstName", "CONTAINS", searchRequest.getFirstName()));
            }

            if (searchRequest.getLastName() != null && !searchRequest.getLastName().trim().isEmpty()) {
                conditions.add(Condition.of("$.lastName", "CONTAINS", searchRequest.getLastName()));
            }

            if (searchRequest.getEmail() != null && !searchRequest.getEmail().trim().isEmpty()) {
                conditions.add(Condition.of("$.email", "EQUALS", searchRequest.getEmail()));
            }

            if (searchRequest.getCity() != null && !searchRequest.getCity().trim().isEmpty()) {
                conditions.add(Condition.of("$.city", "EQUALS", searchRequest.getCity()));
            }

            if (searchRequest.getZipCode() != null && !searchRequest.getZipCode().trim().isEmpty()) {
                conditions.add(Condition.of("$.zipCode", "EQUALS", searchRequest.getZipCode()));
            }

            if (searchRequest.getMinPets() != null) {
                conditions.add(Condition.of("$.totalPets", "GREATER_THAN_OR_EQUAL", searchRequest.getMinPets()));
            }

            if (searchRequest.getMaxPets() != null) {
                conditions.add(Condition.of("$.totalPets", "LESS_THAN_OR_EQUAL", searchRequest.getMaxPets()));
            }

            SearchConditionRequest searchConditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
            List<EntityWithMetadata<Owner>> owners = entityService.search(modelSpec, searchConditionRequest, Owner.class);
            return ResponseEntity.ok(owners);
        } catch (Exception e) {
            logger.error("Error performing advanced owner search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced search requests
     */
    public static class OwnerSearchRequest {
        private String firstName;
        private String lastName;
        private String email;
        private String city;
        private String zipCode;
        private Integer minPets;
        private Integer maxPets;

        // Getters and setters
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        
        public String getZipCode() { return zipCode; }
        public void setZipCode(String zipCode) { this.zipCode = zipCode; }
        
        public Integer getMinPets() { return minPets; }
        public void setMinPets(Integer minPets) { this.minPets = minPets; }
        
        public Integer getMaxPets() { return maxPets; }
        public void setMaxPets(Integer maxPets) { this.maxPets = maxPets; }
    }
}
