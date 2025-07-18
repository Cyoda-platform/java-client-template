package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    // PetJob caches and ID counter
    private final ConcurrentHashMap<String, com.java_template.application.entity.PetJob> petJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petJobIdCounter = new AtomicLong(1);

    // Pet caches and ID counter
    private final ConcurrentHashMap<String, com.java_template.application.entity.Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // DTO for PetJob POST/PUT validation
    public static class PetJobDto {
        @NotBlank
        @Size(max = 50)
        private String action;

        @NotBlank
        @Size(max = 36)
        @Pattern(regexp = "^[0-9a-fA-F\\-]{36}$", message = "petId must be a valid UUID string")
        private String petId;

        // getters and setters
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public String getPetId() { return petId; }
        public void setPetId(String petId) { this.petId = petId; }
    }

    // DTO for Pet POST/PUT validation
    public static class PetDto {
        @NotBlank
        @Size(max = 36)
        @Pattern(regexp = "^[0-9a-fA-F\\-]{36}$", message = "petId must be a valid UUID string")
        private String petId;

        @NotBlank
        @Size(max = 100)
        private String name;

        @NotBlank
        @Size(max = 50)
        private String category;

        @NotBlank
        @Size(max = 20)
        private String status;

        // getters and setters
        public String getPetId() { return petId; }
        public void setPetId(String petId) { this.petId = petId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    // --- PetJob endpoints ---

    @PostMapping("/petjobs")
    public ResponseEntity<?> createPetJob(@RequestBody @Valid PetJobDto petJobDto) {
        com.java_template.application.entity.PetJob petJob = new com.java_template.application.entity.PetJob();
        petJob.setAction(petJobDto.getAction());
        petJob.setPetId(petJobDto.getPetId());

        if (!petJob.isValid()) {
            logger.error("Invalid PetJob data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob data");
        }
        String id = String.valueOf(petJobIdCounter.getAndIncrement());
        petJob.setId(id);
        petJob.setTechnicalId(UUID.randomUUID());
        petJobCache.put(id, petJob);
        logger.info("Created PetJob with id {}", id);

        try {
            processPetJob(petJob);
        } catch (Exception e) {
            logger.error("Error processing PetJob with id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetJob");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
    }

    @GetMapping("/petjobs/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable @NotBlank String id) {
        com.java_template.application.entity.PetJob petJob = petJobCache.get(id);
        if (petJob == null) {
            logger.error("PetJob not found with id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(petJob);
    }

    @PutMapping("/petjobs/{id}")
    public ResponseEntity<?> updatePetJob(
            @PathVariable @NotBlank String id,
            @RequestBody @Valid PetJobDto petJobDto) {
        if (!petJobCache.containsKey(id)) {
            logger.error("PetJob not found with id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        com.java_template.application.entity.PetJob petJob = petJobCache.get(id);
        petJob.setAction(petJobDto.getAction());
        petJob.setPetId(petJobDto.getPetId());

        if (!petJob.isValid()) {
            logger.error("Invalid PetJob data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob data");
        }

        petJob.setId(id);
        petJob.setTechnicalId(petJobCache.get(id).getTechnicalId());
        petJobCache.put(id, petJob);
        logger.info("Updated PetJob with id {}", id);

        try {
            processPetJob(petJob);
        } catch (Exception e) {
            logger.error("Error processing PetJob with id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing PetJob");
        }

        return ResponseEntity.ok(petJob);
    }

    @DeleteMapping("/petjobs/{id}")
    public ResponseEntity<?> deletePetJob(@PathVariable @NotBlank String id) {
        com.java_template.application.entity.PetJob removed = petJobCache.remove(id);
        if (removed == null) {
            logger.error("PetJob not found with id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        logger.info("Deleted PetJob with id {}", id);
        return ResponseEntity.ok("PetJob deleted");
    }

    // --- Pet endpoints ---

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody @Valid PetDto petDto) {
        com.java_template.application.entity.Pet pet = new com.java_template.application.entity.Pet();
        pet.setPetId(petDto.getPetId());
        pet.setName(petDto.getName());
        pet.setCategory(petDto.getCategory());
        pet.setStatus(petDto.getStatus());

        if (!pet.isValid()) {
            logger.error("Invalid Pet data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data");
        }
        String id = String.valueOf(petIdCounter.getAndIncrement());
        pet.setId(id);
        pet.setTechnicalId(UUID.randomUUID());
        petCache.put(id, pet);
        logger.info("Created Pet with id {}", id);

        try {
            processPet(pet);
        } catch (Exception e) {
            logger.error("Error processing Pet with id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing Pet");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable @NotBlank String id) {
        com.java_template.application.entity.Pet pet = petCache.get(id);
        if (pet == null) {
            logger.error("Pet not found with id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @PutMapping("/pets/{id}")
    public ResponseEntity<?> updatePet(
            @PathVariable @NotBlank String id,
            @RequestBody @Valid PetDto petDto) {
        if (!petCache.containsKey(id)) {
            logger.error("Pet not found with id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        com.java_template.application.entity.Pet pet = petCache.get(id);
        pet.setPetId(petDto.getPetId());
        pet.setName(petDto.getName());
        pet.setCategory(petDto.getCategory());
        pet.setStatus(petDto.getStatus());

        if (!pet.isValid()) {
            logger.error("Invalid Pet data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data");
        }

        pet.setId(id);
        pet.setTechnicalId(petCache.get(id).getTechnicalId());
        petCache.put(id, pet);
        logger.info("Updated Pet with id {}", id);

        try {
            processPet(pet);
        } catch (Exception e) {
            logger.error("Error processing Pet with id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing Pet");
        }

        return ResponseEntity.ok(pet);
    }

    @DeleteMapping("/pets/{id}")
    public ResponseEntity<?> deletePet(@PathVariable @NotBlank String id) {
        com.java_template.application.entity.Pet removed = petCache.remove(id);
        if (removed == null) {
            logger.error("Pet not found with id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        logger.info("Deleted Pet with id {}", id);
        return ResponseEntity.ok("Pet deleted");
    }

    // --- Event-driven process methods ---

    private void processPetJob(com.java_template.application.entity.PetJob petJob) {
        logger.info("Processing PetJob with ID: {}", petJob.getId());

        // Implement actual business logic here:
        // - Validate action and petId
        // - Perform create/update/delete on Pet entity cache accordingly
        // - Update PetJob status to COMPLETED or FAILED

        try {
            switch (petJob.getAction().toUpperCase()) {
                case "CREATE":
                    com.java_template.application.entity.Pet newPet = new com.java_template.application.entity.Pet();
                    newPet.setId(UUID.randomUUID().toString());
                    newPet.setTechnicalId(UUID.randomUUID());
                    newPet.setPetId(petJob.getPetId());
                    newPet.setName("New Pet"); // TODO: replace with real data
                    newPet.setCategory("Unknown");
                    newPet.setStatus("AVAILABLE");
                    petCache.put(newPet.getId(), newPet);
                    petJob.setStatus("COMPLETED");
                    break;
                case "UPDATE":
                    // TODO: Implement update logic
                    petJob.setStatus("COMPLETED");
                    break;
                case "DELETE":
                    // TODO: Implement delete logic
                    petJob.setStatus("COMPLETED");
                    break;
                default:
                    logger.error("Unsupported action: {}", petJob.getAction());
                    petJob.setStatus("FAILED");
                    break;
            }
        } catch (Exception e) {
            logger.error("Exception processing PetJob: {}", e.getMessage());
            petJob.setStatus("FAILED");
        }
    }

    private void processPet(com.java_template.application.entity.Pet pet) {
        logger.info("Processing Pet with ID: {}", pet.getId());

        // Implement actual business logic here:
        // - Validate required fields
        // - Update search indexes or related caches if needed
        // - Prepare Pet data for retrieval

        // TODO: Implement enrichment or external API calls if required
    }
}