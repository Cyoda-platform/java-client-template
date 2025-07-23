package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.PetAdoptionTask;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, PetJob> petJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, PetAdoptionTask> petAdoptionTaskCache = new ConcurrentHashMap<>();
    private final AtomicLong petAdoptionTaskIdCounter = new AtomicLong(1);

    // POST /prototype/petjobs - create PetJob event
    @PostMapping("/petjobs")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) {
        if (petJob == null || !petJob.isValid()) {
            log.error("Invalid PetJob creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetJob data.");
        }
        String newId = "job-" + petJobIdCounter.getAndIncrement();
        petJob.setId(newId);
        petJob.setStatus("PENDING");
        petJob.setCreatedAt(java.time.LocalDateTime.now());
        petJobCache.put(newId, petJob);

        try {
            processPetJob(petJob);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("jobId", newId, "status", petJob.getStatus()));
        } catch (Exception e) {
            log.error("Error processing PetJob: {}", e.getMessage());
            petJob.setStatus("FAILED");
            petJobCache.put(newId, petJob);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process PetJob.");
        }
    }

    // GET /prototype/petjobs/{id} - retrieve PetJob by id
    @GetMapping("/petjobs/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) {
        PetJob petJob = petJobCache.get(id);
        if (petJob == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found.");
        }
        return ResponseEntity.ok(petJob);
    }

    // POST /prototype/pets - create Pet event (immutable)
    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null || !pet.isValid()) {
            log.error("Invalid Pet creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data.");
        }
        String newId = "pet-" + petIdCounter.getAndIncrement();
        pet.setId(newId);
        pet.setStatus("ACTIVE");
        petCache.put(newId, pet);

        try {
            processPet(pet);
            return ResponseEntity.status(HttpStatus.CREATED).body(pet);
        } catch (Exception e) {
            log.error("Error processing Pet: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process Pet.");
        }
    }

    // GET /prototype/pets/{id} - retrieve Pet by id
    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found.");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /prototype/pets/{id}/update - create new Pet version (immutable)
    @PostMapping("/pets/{id}/update")
    public ResponseEntity<?> updatePet(@PathVariable String id, @RequestBody Pet petUpdate) {
        if (petUpdate == null || !petUpdate.isValid()) {
            log.error("Invalid Pet update request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet update data.");
        }
        Pet existingPet = petCache.get(id);
        if (existingPet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found.");
        }
        // Create new Pet instance as new version with new id
        String newId = "pet-" + petIdCounter.getAndIncrement();
        petUpdate.setId(newId);
        petUpdate.setStatus(existingPet.getStatus()); // preserve status unless changed explicitly
        petCache.put(newId, petUpdate);
        try {
            processPet(petUpdate);
            return ResponseEntity.status(HttpStatus.CREATED).body(petUpdate);
        } catch (Exception e) {
            log.error("Error processing Pet update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process Pet update.");
        }
    }

    // POST /prototype/pets/{id}/deactivate - create deactivation record (immutable)
    @PostMapping("/pets/{id}/deactivate")
    public ResponseEntity<?> deactivatePet(@PathVariable String id) {
        Pet existingPet = petCache.get(id);
        if (existingPet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found.");
        }
        // Create new Pet instance with status INACTIVE
        Pet deactivatedPet = new Pet();
        deactivatedPet.setId("pet-" + petIdCounter.getAndIncrement());
        deactivatedPet.setName(existingPet.getName());
        deactivatedPet.setSpecies(existingPet.getSpecies());
        deactivatedPet.setBreed(existingPet.getBreed());
        deactivatedPet.setAge(existingPet.getAge());
        deactivatedPet.setAdoptionStatus(existingPet.getAdoptionStatus());
        deactivatedPet.setStatus("INACTIVE");
        petCache.put(deactivatedPet.getId(), deactivatedPet);
        log.info("Pet with id {} deactivated, new version id {}", id, deactivatedPet.getId());
        return ResponseEntity.ok(Map.of("message", "Pet deactivated", "newId", deactivatedPet.getId()));
    }

    // POST /prototype/petadoptiontasks - create PetAdoptionTask event
    @PostMapping("/petadoptiontasks")
    public ResponseEntity<?> createPetAdoptionTask(@RequestBody PetAdoptionTask task) {
        if (task == null || !task.isValid()) {
            log.error("Invalid PetAdoptionTask creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetAdoptionTask data.");
        }
        String newId = "task-" + petAdoptionTaskIdCounter.getAndIncrement();
        task.setId(newId);
        task.setStatus("PENDING");
        task.setCreatedAt(java.time.LocalDateTime.now());
        petAdoptionTaskCache.put(newId, task);

        try {
            processPetAdoptionTask(task);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("taskId", newId, "status", task.getStatus()));
        } catch (Exception e) {
            log.error("Error processing PetAdoptionTask: {}", e.getMessage());
            task.setStatus("FAILED");
            petAdoptionTaskCache.put(newId, task);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process PetAdoptionTask.");
        }
    }

    // GET /prototype/petadoptiontasks/{id} - retrieve PetAdoptionTask by id
    @GetMapping("/petadoptiontasks/{id}")
    public ResponseEntity<?> getPetAdoptionTask(@PathVariable String id) {
        PetAdoptionTask task = petAdoptionTaskCache.get(id);
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetAdoptionTask not found.");
        }
        return ResponseEntity.ok(task);
    }

    // Business logic for PetJob processing
    private void processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());
        petJob.setStatus("PROCESSING");

        // Validate petId and action
        if (petJob.getPetId() == null || petJob.getPetId().isBlank()) {
            log.error("PetJob {} validation failed: petId is blank", petJob.getId());
            petJob.setStatus("FAILED");
            petJobCache.put(petJob.getId(), petJob);
            return;
        }
        if (petJob.getAction() == null || petJob.getAction().isBlank()) {
            log.error("PetJob {} validation failed: action is blank", petJob.getId());
            petJob.setStatus("FAILED");
            petJobCache.put(petJob.getId(), petJob);
            return;
        }

        try {
            switch (petJob.getAction()) {
                case "CREATE":
                    // Create new Pet entity with ACTIVE status
                    Pet newPet = new Pet();
                    newPet.setId("pet-" + petIdCounter.getAndIncrement());
                    newPet.setName("New Pet"); // placeholder; in real case should come from input or external source
                    newPet.setSpecies("Unknown");
                    newPet.setBreed("Unknown");
                    newPet.setAge(0);
                    newPet.setAdoptionStatus("AVAILABLE");
                    newPet.setStatus("ACTIVE");
                    petCache.put(newPet.getId(), newPet);
                    log.info("Created new Pet {} from PetJob {}", newPet.getId(), petJob.getId());
                    break;
                case "UPDATE":
                    // Create new immutable Pet snapshot with updated data - simplified here
                    Pet existingPet = petCache.get(petJob.getPetId());
                    if (existingPet == null) {
                        log.error("PetJob {} update failed: Pet not found", petJob.getId());
                        petJob.setStatus("FAILED");
                        petJobCache.put(petJob.getId(), petJob);
                        return;
                    }
                    Pet updatedPet = new Pet();
                    updatedPet.setId("pet-" + petIdCounter.getAndIncrement());
                    updatedPet.setName(existingPet.getName());
                    updatedPet.setSpecies(existingPet.getSpecies());
                    updatedPet.setBreed(existingPet.getBreed());
                    updatedPet.setAge(existingPet.getAge());
                    updatedPet.setAdoptionStatus(existingPet.getAdoptionStatus());
                    updatedPet.setStatus(existingPet.getStatus());
                    petCache.put(updatedPet.getId(), updatedPet);
                    log.info("Created updated Pet version {} from PetJob {}", updatedPet.getId(), petJob.getId());
                    break;
                case "STATUS_CHANGE":
                    Pet petToChange = petCache.get(petJob.getPetId());
                    if (petToChange == null) {
                        log.error("PetJob {} status change failed: Pet not found", petJob.getId());
                        petJob.setStatus("FAILED");
                        petJobCache.put(petJob.getId(), petJob);
                        return;
                    }
                    Pet statusChangedPet = new Pet();
                    statusChangedPet.setId("pet-" + petIdCounter.getAndIncrement());
                    statusChangedPet.setName(petToChange.getName());
                    statusChangedPet.setSpecies(petToChange.getSpecies());
                    statusChangedPet.setBreed(petToChange.getBreed());
                    statusChangedPet.setAge(petToChange.getAge());
                    statusChangedPet.setAdoptionStatus("ADOPTED"); // example status change
                    statusChangedPet.setStatus(petToChange.getStatus());
                    petCache.put(statusChangedPet.getId(), statusChangedPet);
                    log.info("Created Pet status changed version {} from PetJob {}", statusChangedPet.getId(), petJob.getId());
                    break;
                default:
                    log.error("PetJob {} unknown action: {}", petJob.getId(), petJob.getAction());
                    petJob.setStatus("FAILED");
                    petJobCache.put(petJob.getId(), petJob);
                    return;
            }
            petJob.setStatus("COMPLETED");
            petJobCache.put(petJob.getId(), petJob);
        } catch (Exception e) {
            log.error("PetJob {} processing error: {}", petJob.getId(), e.getMessage());
            petJob.setStatus("FAILED");
            petJobCache.put(petJob.getId(), petJob);
        }
    }

    // Business logic for PetAdoptionTask processing
    private void processPetAdoptionTask(PetAdoptionTask task) {
        log.info("Processing PetAdoptionTask with ID: {}", task.getId());
        task.setStatus("PROCESSING");

        if (task.getPetId() == null || task.getPetId().isBlank()) {
            log.error("PetAdoptionTask {} validation failed: petId is blank", task.getId());
            task.setStatus("FAILED");
            petAdoptionTaskCache.put(task.getId(), task);
            return;
        }
        if (task.getTaskType() == null || task.getTaskType().isBlank()) {
            log.error("PetAdoptionTask {} validation failed: taskType is blank", task.getId());
            task.setStatus("FAILED");
            petAdoptionTaskCache.put(task.getId(), task);
            return;
        }

        try {
            // Example processing logic - notify adoption team or trigger next workflow step
            log.info("PetAdoptionTask {} processing task type: {}", task.getId(), task.getTaskType());

            // Simulate processing delay or external calls here if needed

            task.setStatus("COMPLETED");
            petAdoptionTaskCache.put(task.getId(), task);

        } catch (Exception e) {
            log.error("PetAdoptionTask {} processing error: {}", task.getId(), e.getMessage());
            task.setStatus("FAILED");
            petAdoptionTaskCache.put(task.getId(), task);
        }
    }

    // Business logic for Pet processing
    private void processPet(Pet pet) {
        log.info("Processing Pet with ID: {}", pet.getId());
        // Validate pet fields - already done in isValid()
        // Enrich pet data if necessary
        // External API calls can be simulated here if required
        // For prototype, just log and accept the pet
        log.info("Pet {} processed successfully", pet.getId());
    }
}