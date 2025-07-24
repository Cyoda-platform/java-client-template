package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.Pet;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PetJob> petJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<Long, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // --------- PetJob Endpoints ---------

    @PostMapping("/pet-job")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) {
        if (petJob == null) {
            log.error("PetJob creation failed: request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PetJob data is required");
        }
        // Assign business ID as string of counter
        petJob.setId("PJ-" + petJobIdCounter.getAndIncrement());
        petJob.setStatus("PENDING");
        if (petJob.getTechnicalId() == null) {
            petJob.setTechnicalId(UUID.randomUUID());
        }
        if (!petJob.isValid()) {
            log.error("PetJob creation failed: invalid data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob data");
        }

        petJobCache.put(petJob.getId(), petJob);
        log.info("PetJob created with ID: {}", petJob.getId());

        processPetJob(petJob);

        Map<String, String> resp = new HashMap<>();
        resp.put("jobId", petJob.getId());
        resp.put("status", petJob.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping("/pet-job/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) {
        PetJob petJob = petJobCache.get(id);
        if (petJob == null) {
            log.error("PetJob not found: ID {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(petJob);
    }

    // --------- Pet Endpoints ---------

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            log.error("Pet creation failed: request body is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet data is required");
        }
        // Assign business ID using atomic counter
        pet.setId(petIdCounter.getAndIncrement());
        if (pet.getTechnicalId() == null) {
            pet.setTechnicalId(UUID.randomUUID());
        }
        if (!pet.isValid()) {
            log.error("Pet creation failed: invalid data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data");
        }

        petCache.put(pet.getId(), pet);
        log.info("Pet created with ID: {}", pet.getId());

        processPet(pet);

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", pet.getId());
        resp.put("status", pet.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable Long id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found: ID {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // --------- Process Methods ---------

    private void processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());
        // Business logic based on operation
        try {
            petJob.setStatus("PROCESSING");

            String operation = petJob.getOperation().toUpperCase(Locale.ROOT);

            switch (operation) {
                case "CREATE":
                    // Parse payload and create new Pet entity
                    // For simplicity, assuming requestPayload is JSON string representing a Pet
                    Pet newPet = JsonUtil.fromJson(petJob.getRequestPayload(), Pet.class);
                    if (newPet == null || !newPet.isValid()) {
                        petJob.setStatus("FAILED");
                        log.error("PetJob CREATE operation failed: invalid pet data in payload");
                        break;
                    }
                    // Assign new ID and technicalId
                    newPet.setId(petIdCounter.getAndIncrement());
                    newPet.setTechnicalId(UUID.randomUUID());
                    petCache.put(newPet.getId(), newPet);
                    processPet(newPet);
                    petJob.setStatus("COMPLETED");
                    log.info("PetJob CREATE operation completed: Pet ID {}", newPet.getId());
                    break;

                case "PROCESS":
                    // Example: enrich pet data (could be extended)
                    if (petJob.getPetId() == null) {
                        petJob.setStatus("FAILED");
                        log.error("PetJob PROCESS operation failed: petId is missing");
                        break;
                    }
                    Pet petToProcess = petCache.get(petJob.getPetId());
                    if (petToProcess == null) {
                        petJob.setStatus("FAILED");
                        log.error("PetJob PROCESS operation failed: Pet not found with ID {}", petJob.getPetId());
                        break;
                    }
                    // Example enrichment: add a tag if missing
                    if (petToProcess.getTags() == null) {
                        petToProcess.setTags(new ArrayList<>());
                    }
                    if (!petToProcess.getTags().contains("processed")) {
                        petToProcess.getTags().add("processed");
                    }
                    petJob.setStatus("COMPLETED");
                    log.info("PetJob PROCESS operation completed for Pet ID {}", petToProcess.getId());
                    break;

                case "SEARCH":
                    // Search pets by criteria in requestPayload (JSON)
                    Map<String, String> criteria = JsonUtil.fromJson(petJob.getRequestPayload(), Map.class);
                    if (criteria == null || criteria.isEmpty()) {
                        petJob.setStatus("FAILED");
                        log.error("PetJob SEARCH operation failed: empty or invalid criteria");
                        break;
                    }
                    List<Pet> results = new ArrayList<>();
                    for (Pet pet : petCache.values()) {
                        boolean match = true;
                        for (Map.Entry<String, String> entry : criteria.entrySet()) {
                            String key = entry.getKey().toLowerCase(Locale.ROOT);
                            String value = entry.getValue().toLowerCase(Locale.ROOT);
                            switch (key) {
                                case "category":
                                    if (pet.getCategory() == null || !pet.getCategory().toLowerCase(Locale.ROOT).equals(value)) {
                                        match = false;
                                    }
                                    break;
                                case "status":
                                    if (pet.getStatus() == null || !pet.getStatus().toLowerCase(Locale.ROOT).equals(value)) {
                                        match = false;
                                    }
                                    break;
                                case "name":
                                    if (pet.getName() == null || !pet.getName().toLowerCase(Locale.ROOT).contains(value)) {
                                        match = false;
                                    }
                                    break;
                                default:
                                    // ignore unknown criteria
                            }
                            if (!match) break;
                        }
                        if (match) {
                            results.add(pet);
                        }
                    }
                    // For prototype, just log the count and don't store result inside PetJob
                    log.info("PetJob SEARCH operation found {} pets matching criteria", results.size());
                    petJob.setStatus("COMPLETED");
                    break;

                default:
                    petJob.setStatus("FAILED");
                    log.error("PetJob operation '{}' is not supported", operation);
            }
        } catch (Exception e) {
            petJob.setStatus("FAILED");
            log.error("Exception during PetJob processing: {}", e.getMessage());
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Validate required fields (already done in isValid)
        // For prototype, no external API calls, just log and finalize status
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("available"); // default to available if not specified
        }
        log.info("Pet processing completed with status: {}", pet.getStatus());
    }

    // Utility class for JSON processing - simplified placeholder
    private static class JsonUtil {
        // Use your JSON library here, e.g. Jackson, Gson
        // For prototype, stub methods:

        public static <T> T fromJson(String json, Class<T> clazz) {
            // This is a stub. Replace with actual JSON deserialization.
            return null;
        }

        public static <T> T fromJson(String json, java.lang.reflect.Type typeOfT) {
            // This is a stub for generic type deserialization.
            return null;
        }
    }
}