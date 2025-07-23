package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PurrfectPetJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;

    private static final String PURRFECT_PET_JOB_ENTITY = "PurrfectPetJob";
    private static final String PET_ENTITY = "Pet";

    // --- PurrfectPetJob Endpoints ---

    @PostMapping("/purrfectPetJob")
    public CompletableFuture<ResponseEntity<?>> createPurrfectPetJob(@RequestBody PurrfectPetJob job) {
        if (job == null) {
            log.error("Received null PurrfectPetJob");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Job cannot be null"));
        }

        if (!job.isValid()) {
            log.error("Invalid PurrfectPetJob: {}", job);
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Invalid job data"));
        }

        job.setStatus("PENDING");

        // add job via entityService
        return entityService.addItem(PURRFECT_PET_JOB_ENTITY, ENTITY_VERSION, job)
                .thenApply(technicalId -> {
                    job.setTechnicalId(technicalId);
                    job.setId("job-" + technicalId.toString());

                    log.info("Created PurrfectPetJob with technicalId: {}", technicalId);

                    processPurrfectPetJob(job);

                    return ResponseEntity.status(HttpStatus.CREATED).body(job);
                });
    }

    @GetMapping("/purrfectPetJob/{id}")
    public CompletableFuture<ResponseEntity<?>> getPurrfectPetJob(@PathVariable String id) {
        // id is expected to be "job-<UUID>", extract the UUID part
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id.replace("job-", ""));
        } catch (Exception e) {
            log.error("Invalid PurrfectPetJob id format: {}", id);
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid job id"));
        }

        return entityService.getItem(PURRFECT_PET_JOB_ENTITY, ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        log.error("PurrfectPetJob not found with technicalId: {}", technicalId);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
                    }
                    // Map ObjectNode to PurrfectPetJob
                    PurrfectPetJob job = objectNode.traverse().readValueAs(PurrfectPetJob.class);
                    job.setTechnicalId(technicalId);
                    job.setId("job-" + technicalId.toString());
                    return ResponseEntity.ok(job);
                });
    }

    // --- Pet Endpoints ---

    @PostMapping("/pet")
    public CompletableFuture<ResponseEntity<?>> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            log.error("Received null Pet");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Pet cannot be null"));
        }

        if (!pet.isValid()) {
            log.error("Invalid Pet data: {}", pet);
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Invalid pet data"));
        }

        pet.setStatus("CREATED");

        return entityService.addItem(PET_ENTITY, ENTITY_VERSION, pet)
                .thenApply(technicalId -> {
                    pet.setTechnicalId(technicalId);
                    pet.setId("pet-" + technicalId.toString());

                    log.info("Created Pet with technicalId: {}", technicalId);

                    processPet(pet);

                    return ResponseEntity.status(HttpStatus.CREATED).body(pet);
                });
    }

    @GetMapping("/pet/{id}")
    public CompletableFuture<ResponseEntity<?>> getPet(@PathVariable String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id.replace("pet-", ""));
        } catch (Exception e) {
            log.error("Invalid Pet id format: {}", id);
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid pet id"));
        }

        return entityService.getItem(PET_ENTITY, ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        log.error("Pet not found with technicalId: {}", technicalId);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
                    }
                    Pet pet = objectNode.traverse().readValueAs(Pet.class);
                    pet.setTechnicalId(technicalId);
                    pet.setId("pet-" + technicalId.toString());
                    return ResponseEntity.ok(pet);
                });
    }

    // --- PetEvent Endpoints (minor entity, keep local cache style) ---

    private final java.util.concurrent.ConcurrentHashMap<String, PetEvent> petEventCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong petEventIdCounter = new java.util.concurrent.atomic.AtomicLong(1);

    @PostMapping("/petEvent")
    public ResponseEntity<?> createPetEvent(@RequestBody PetEvent petEvent) {
        if (petEvent == null) {
            log.error("Received null PetEvent");
            return ResponseEntity.badRequest().body("PetEvent cannot be null");
        }

        petEvent.setId("event-" + petEventIdCounter.getAndIncrement());
        petEvent.setTechnicalId(UUID.randomUUID());

        if (!petEvent.isValid()) {
            log.error("Invalid PetEvent data: {}", petEvent);
            return ResponseEntity.badRequest().body("Invalid pet event data");
        }

        petEventCache.put(petEvent.getId(), petEvent);

        log.info("Created PetEvent with ID: {}", petEvent.getId());

        processPetEvent(petEvent);

        return ResponseEntity.status(HttpStatus.CREATED).body(petEvent);
    }

    @GetMapping("/petEvent/{id}")
    public ResponseEntity<?> getPetEvent(@PathVariable String id) {
        PetEvent petEvent = petEventCache.get(id);
        if (petEvent == null) {
            log.error("PetEvent not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetEvent not found");
        }
        return ResponseEntity.ok(petEvent);
    }

    // --- Process Methods ---

    private void processPurrfectPetJob(PurrfectPetJob job) {
        log.info("Processing PurrfectPetJob with technicalId: {}", job.getTechnicalId());

        if (job.getPetType() == null || job.getPetType().isBlank() ||
                job.getAction() == null || job.getAction().isBlank()) {
            log.error("Invalid job parameters for technicalId: {}", job.getTechnicalId());
            job.setStatus("FAILED");
            return;
        }

        job.setStatus("PROCESSING");

        try {
            if ("ADD".equalsIgnoreCase(job.getAction())) {

                Pet newPet = new Pet();
                newPet.setTechnicalId(UUID.randomUUID());
                newPet.setType(job.getPetType());
                newPet.setStatus("CREATED");

                String payload = job.getPayload();
                if (payload != null && !payload.isBlank()) {
                    if (payload.contains("\"name\"")) {
                        int start = payload.indexOf("\"name\"") + 7;
                        int end = payload.indexOf("\"", start);
                        newPet.setName(payload.substring(start, end));
                    }
                    if (payload.contains("\"age\"")) {
                        int start = payload.indexOf("\"age\"") + 6;
                        int end = payload.indexOf("}", start);
                        try {
                            newPet.setAge(Integer.parseInt(payload.substring(start, end).trim()));
                        } catch (NumberFormatException e) {
                            newPet.setAge(0);
                        }
                    }
                }
                newPet.setAdoptionStatus("AVAILABLE");

                entityService.addItem(PET_ENTITY, ENTITY_VERSION, newPet)
                        .thenAccept(technicalId -> {
                            newPet.setTechnicalId(technicalId);
                            newPet.setId("pet-" + technicalId.toString());
                            processPet(newPet);

                            PetEvent event = new PetEvent();
                            event.setId("event-" + petEventIdCounter.getAndIncrement());
                            event.setTechnicalId(UUID.randomUUID());
                            event.setPetId(newPet.getId());
                            event.setEventType("CREATED");
                            event.setTimestamp(LocalDateTime.now());
                            event.setStatus("RECORDED");
                            petEventCache.put(event.getId(), event);
                            processPetEvent(event);
                        }).join();

            } else if ("SEARCH".equalsIgnoreCase(job.getAction())) {
                log.info("Search action received for petType: {}", job.getPetType());
            } else {
                log.warn("Unknown action: {} for technicalId: {}", job.getAction(), job.getTechnicalId());
                job.setStatus("FAILED");
                return;
            }

            job.setStatus("COMPLETED");
            log.info("Completed processing PurrfectPetJob with technicalId: {}", job.getTechnicalId());

        } catch (Exception e) {
            log.error("Exception processing PurrfectPetJob with technicalId: {}", job.getTechnicalId(), e);
            job.setStatus("FAILED");
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with technicalId: {}", pet.getTechnicalId());

        if (pet.getName() == null || pet.getName().isBlank() ||
                pet.getType() == null || pet.getType().isBlank()) {
            log.error("Invalid pet data for technicalId: {}", pet.getTechnicalId());
            pet.setStatus("ARCHIVED");
            return;
        }

        if (pet.getAge() == null) {
            pet.setAge(0);
        }

        pet.setStatus("ACTIVE");

        log.info("Pet processing completed for technicalId: {}", pet.getTechnicalId());
    }

    private void processPetEvent(PetEvent petEvent) {
        log.info("Processing PetEvent with ID: {}", petEvent.getId());

        if (petEvent.getPetId() == null || petEvent.getPetId().isBlank() ||
                petEvent.getEventType() == null || petEvent.getEventType().isBlank()) {
            log.error("Invalid PetEvent data for event ID: {}", petEvent.getId());
            petEvent.setStatus("PROCESSED");
            return;
        }

        if ("ADOPTED".equalsIgnoreCase(petEvent.getEventType())) {
            // update pet adoption status in local cache if exists
            Pet pet = null;
            try {
                UUID petTechnicalId = UUID.fromString(petEvent.getPetId().replace("pet-", ""));
                CompletableFuture<ObjectNode> petNodeFuture = entityService.getItem(PET_ENTITY, ENTITY_VERSION, petTechnicalId);
                ObjectNode petNode = petNodeFuture.join();
                if (petNode != null && !petNode.isEmpty()) {
                    pet = petNode.traverse().readValueAs(Pet.class);
                    pet.setTechnicalId(petTechnicalId);
                    pet.setId(petEvent.getPetId());
                    pet.setAdoptionStatus("ADOPTED");
                    entityService.addItem(PET_ENTITY, ENTITY_VERSION, pet).join();
                    log.info("Updated adoption status to ADOPTED for pet technicalId: {}", petTechnicalId);
                } else {
                    log.warn("Pet not found for PetEvent ID: {}, petId: {}", petEvent.getId(), petEvent.getPetId());
                }
            } catch (Exception e) {
                log.error("Error updating adoption status for PetEvent ID: {}, petId: {}", petEvent.getId(), petEvent.getPetId(), e);
            }
        }

        petEvent.setStatus("PROCESSED");

        log.info("PetEvent processing completed for ID: {}", petEvent.getId());
    }

}