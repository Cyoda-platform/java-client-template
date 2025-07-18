package com.java_template.prototype;

import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping(path = "/prototype/entity")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ConcurrentHashMap<String, List<Job>> jobCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Pet>> petCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<AdoptionRequest>> adoptionRequestCache = new ConcurrentHashMap<>();

    private final AtomicLong jobIdCounter = new AtomicLong(1);
    private final AtomicLong petIdCounter = new AtomicLong(1);
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    // ====== JOB CRUD ======

    @PostMapping("/job")
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody @Valid JobInput jobInput) {
        logger.info("Received request to create Job: {}", jobInput);
        try {
            Job job = convertToJob(jobInput);
            String id = addJob(job);
            logger.info("Job created with id {}", id);
            return ResponseEntity.ok(Map.of("id", id, "status", "Job created and processed"));
        } catch (Exception e) {
            logger.error("Error creating Job", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error creating job");
        }
    }

    @GetMapping("/job")
    // Using @ModelAttribute DTO to validate query params in GET
    public ResponseEntity<Job> getJob(@Valid @ModelAttribute JobQuery query) {
        String id = query.getId();
        logger.info("Received request to get Job with id: {}", id);
        Job job = getEntityById(jobCache, id)
                .orElseThrow(() -> {
                    logger.error("Job with id {} not found", id);
                    return new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
                });
        return ResponseEntity.ok(job);
    }

    @PutMapping("/job")
    public ResponseEntity<Map<String, Object>> updateJob(@RequestBody @Valid JobUpdate jobUpdate) {
        String id = jobUpdate.getId();
        logger.info("Received request to update Job with id {}: {}", id, jobUpdate);
        try {
            Job job = convertToJob(jobUpdate);
            updateEntity(jobCache, id, job);
            processJob(job);
            return ResponseEntity.ok(Map.of("id", id, "status", "Job updated and processed"));
        } catch (ResponseStatusException ex) {
            logger.error("Job update failed: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            logger.error("Error updating Job", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error updating job");
        }
    }

    @DeleteMapping("/job")
    public ResponseEntity<Map<String, Object>> deleteJob(@RequestParam @NotBlank String id) {
        logger.info("Received request to delete Job with id {}", id);
        try {
            deleteEntity(jobCache, id);
            return ResponseEntity.ok(Map.of("id", id, "status", "Job deleted"));
        } catch (ResponseStatusException ex) {
            logger.error("Job delete failed: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            logger.error("Error deleting Job", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting job");
        }
    }

    // ====== PET CRUD ======

    @PostMapping("/pet")
    public ResponseEntity<Map<String, Object>> createPet(@RequestBody @Valid PetInput petInput) {
        logger.info("Received request to create Pet: {}", petInput);
        try {
            Pet pet = convertToPet(petInput);
            String id = addPet(pet);
            logger.info("Pet created with id {}", id);
            return ResponseEntity.ok(Map.of("id", id, "status", "Pet created and processed"));
        } catch (Exception e) {
            logger.error("Error creating Pet", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error creating pet");
        }
    }

    @GetMapping("/pet")
    public ResponseEntity<Pet> getPet(@Valid @ModelAttribute PetQuery query) {
        String id = query.getId();
        logger.info("Received request to get Pet with id: {}", id);
        Pet pet = getEntityById(petCache, id)
                .orElseThrow(() -> {
                    logger.error("Pet with id {} not found", id);
                    return new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
                });
        return ResponseEntity.ok(pet);
    }

    @PutMapping("/pet")
    public ResponseEntity<Map<String, Object>> updatePet(@RequestBody @Valid PetUpdate petUpdate) {
        String id = petUpdate.getId();
        logger.info("Received request to update Pet with id {}: {}", id, petUpdate);
        try {
            Pet pet = convertToPet(petUpdate);
            updateEntity(petCache, id, pet);
            processPet(pet);
            return ResponseEntity.ok(Map.of("id", id, "status", "Pet updated and processed"));
        } catch (ResponseStatusException ex) {
            logger.error("Pet update failed: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            logger.error("Error updating Pet", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error updating pet");
        }
    }

    @DeleteMapping("/pet")
    public ResponseEntity<Map<String, Object>> deletePet(@RequestParam @NotBlank String id) {
        logger.info("Received request to delete Pet with id {}", id);
        try {
            deleteEntity(petCache, id);
            return ResponseEntity.ok(Map.of("id", id, "status", "Pet deleted"));
        } catch (ResponseStatusException ex) {
            logger.error("Pet delete failed: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            logger.error("Error deleting Pet", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting pet");
        }
    }

    // ====== ADOPTION REQUEST CRUD ======

    @PostMapping("/adoptionRequest")
    public ResponseEntity<Map<String, Object>> createAdoptionRequest(@RequestBody @Valid AdoptionRequestInput input) {
        logger.info("Received request to create AdoptionRequest: {}", input);
        try {
            AdoptionRequest request = convertToAdoptionRequest(input);
            String id = addAdoptionRequest(request);
            logger.info("AdoptionRequest created with id {}", id);
            return ResponseEntity.ok(Map.of("id", id, "status", "AdoptionRequest created and processed"));
        } catch (Exception e) {
            logger.error("Error creating AdoptionRequest", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error creating adoption request");
        }
    }

    @GetMapping("/adoptionRequest")
    public ResponseEntity<AdoptionRequest> getAdoptionRequest(@Valid @ModelAttribute AdoptionRequestQuery query) {
        String id = query.getId();
        logger.info("Received request to get AdoptionRequest with id: {}", id);
        AdoptionRequest request = getEntityById(adoptionRequestCache, id)
                .orElseThrow(() -> {
                    logger.error("AdoptionRequest with id {} not found", id);
                    return new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
                });
        return ResponseEntity.ok(request);
    }

    @PutMapping("/adoptionRequest")
    public ResponseEntity<Map<String, Object>> updateAdoptionRequest(@RequestBody @Valid AdoptionRequestUpdate input) {
        String id = input.getId();
        logger.info("Received request to update AdoptionRequest with id {}: {}", id, input);
        try {
            AdoptionRequest request = convertToAdoptionRequest(input);
            updateEntity(adoptionRequestCache, id, request);
            processAdoptionRequest(request);
            return ResponseEntity.ok(Map.of("id", id, "status", "AdoptionRequest updated and processed"));
        } catch (ResponseStatusException ex) {
            logger.error("AdoptionRequest update failed: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            logger.error("Error updating AdoptionRequest", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error updating adoption request");
        }
    }

    @DeleteMapping("/adoptionRequest")
    public ResponseEntity<Map<String, Object>> deleteAdoptionRequest(@RequestParam @NotBlank String id) {
        logger.info("Received request to delete AdoptionRequest with id {}", id);
        try {
            deleteEntity(adoptionRequestCache, id);
            return ResponseEntity.ok(Map.of("id", id, "status", "AdoptionRequest deleted"));
        } catch (ResponseStatusException ex) {
            logger.error("AdoptionRequest delete failed: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            logger.error("Error deleting AdoptionRequest", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting adoption request");
        }
    }

    // ====== Generic Cache Operations ======

    private <T extends com.java_template.common.workflow.CyodaEntity> Optional<T> getEntityById(ConcurrentHashMap<String, List<T>> cache, String id) {
        List<T> entities = cache.get("entities");
        if (entities == null) {
            return Optional.empty();
        }
        return entities.stream()
                .filter(e -> id.equals(e.getId()))
                .findFirst();
    }

    private <T extends com.java_template.common.workflow.CyodaEntity> void updateEntity(ConcurrentHashMap<String, List<T>> cache, String id, T updatedEntity) {
        List<T> entities = cache.get("entities");
        if (entities == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Entity not found");
        }
        synchronized (entities) {
            for (int i = 0; i < entities.size(); i++) {
                if (id.equals(entities.get(i).getId())) {
                    updatedEntity.setId(id);
                    updatedEntity.setTechnicalId(entities.get(i).getTechnicalId());
                    entities.set(i, updatedEntity);
                    logger.info("Entity with id {} updated in cache", id);
                    return;
                }
            }
        }
        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Entity not found");
    }

    private <T extends com.java_template.common.workflow.CyodaEntity> void deleteEntity(ConcurrentHashMap<String, List<T>> cache, String id) {
        List<T> entities = cache.get("entities");
        if (entities == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Entity not found");
        }
        synchronized (entities) {
            boolean removed = entities.removeIf(e -> id.equals(e.getId()));
            if (!removed) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Entity not found");
            }
            logger.info("Entity with id {} removed from cache", id);
        }
    }

    // ====== Add Entity Methods with Event Processing ======

    private String addJob(Job job) {
        if (job.getId() != null && !job.getId().isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "New Job cannot have an id");
        }
        String id = String.valueOf(jobIdCounter.getAndIncrement());
        job.setId(id);
        job.setTechnicalId(UUID.randomUUID());
        jobCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(job);
        logger.info("Job added to cache: {}", job);
        processJob(job);
        return id;
    }

    private String addPet(Pet pet) {
        if (pet.getId() != null && !pet.getId().isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "New Pet cannot have an id");
        }
        String id = String.valueOf(petIdCounter.getAndIncrement());
        pet.setId(id);
        pet.setTechnicalId(UUID.randomUUID());
        petCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(pet);
        logger.info("Pet added to cache: {}", pet);
        processPet(pet);
        return id;
    }

    private String addAdoptionRequest(AdoptionRequest request) {
        if (request.getId() != null && !request.getId().isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "New AdoptionRequest cannot have an id");
        }
        String id = String.valueOf(adoptionRequestIdCounter.getAndIncrement());
        request.setId(id);
        request.setTechnicalId(UUID.randomUUID());
        adoptionRequestCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(request);
        logger.info("AdoptionRequest added to cache: {}", request);
        processAdoptionRequest(request);
        return id;
    }

    // ====== Event Processing Simulation ======

    private void processJob(Job job) {
        // TODO: Replace with real event processing logic or integration with Cyoda event system
        logger.info("Processing Job entity event for id {}", job.getId());
    }

    private void processPet(Pet pet) {
        // TODO: Replace with real event processing logic or integration with Cyoda event system
        logger.info("Processing Pet entity event for id {}", pet.getId());
    }

    private void processAdoptionRequest(AdoptionRequest request) {
        // TODO: Replace with real event processing logic or integration with Cyoda event system
        logger.info("Processing AdoptionRequest entity event for id {}", request.getId());
    }

    // ====== Conversion Utilities from DTOs to Entities ======

    private Job convertToJob(JobInput input) {
        Job job = new Job();
        job.setId(null); // id is generated on add
        job.setTechnicalId(null);
        job.setName(input.getName());
        job.setDescription(input.getDescription());
        job.setStatus(input.getStatus());
        return job;
    }

    private Job convertToJob(JobUpdate input) {
        Job job = new Job();
        job.setId(input.getId());
        job.setTechnicalId(null);
        job.setName(input.getName());
        job.setDescription(input.getDescription());
        job.setStatus(input.getStatus());
        return job;
    }

    private Pet convertToPet(PetInput input) {
        Pet pet = new Pet();
        pet.setId(null);
        pet.setTechnicalId(null);
        pet.setName(input.getName());
        pet.setType(input.getType());
        pet.setAge(input.getAge());
        return pet;
    }

    private Pet convertToPet(PetUpdate input) {
        Pet pet = new Pet();
        pet.setId(input.getId());
        pet.setTechnicalId(null);
        pet.setName(input.getName());
        pet.setType(input.getType());
        pet.setAge(input.getAge());
        return pet;
    }

    private AdoptionRequest convertToAdoptionRequest(AdoptionRequestInput input) {
        AdoptionRequest request = new AdoptionRequest();
        request.setId(null);
        request.setTechnicalId(null);
        request.setPetId(input.getPetId());
        request.setApplicantName(input.getApplicantName());
        request.setStatus(input.getStatus());
        return request;
    }

    private AdoptionRequest convertToAdoptionRequest(AdoptionRequestUpdate input) {
        AdoptionRequest request = new AdoptionRequest();
        request.setId(input.getId());
        request.setTechnicalId(null);
        request.setPetId(input.getPetId());
        request.setApplicantName(input.getApplicantName());
        request.setStatus(input.getStatus());
        return request;
    }

    // ====== DTO Classes for Validation ======

    @Data
    public static class JobInput {
        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 255)
        private String description;

        @NotBlank
        @Pattern(regexp = "NEW|RUNNING|COMPLETED|FAILED")
        private String status;
    }

    @Data
    public static class JobUpdate {
        @NotBlank
        private String id;

        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 255)
        private String description;

        @NotBlank
        @Pattern(regexp = "NEW|RUNNING|COMPLETED|FAILED")
        private String status;
    }

    @Data
    public static class JobQuery {
        @NotBlank
        private String id;
    }

    @Data
    public static class PetInput {
        @NotBlank
        @Size(max = 50)
        private String name;

        @NotBlank
        @Size(max = 30)
        private String type;

        @NotNull
        private Integer age;
    }

    @Data
    public static class PetUpdate {
        @NotBlank
        private String id;

        @NotBlank
        @Size(max = 50)
        private String name;

        @NotBlank
        @Size(max = 30)
        private String type;

        @NotNull
        private Integer age;
    }

    @Data
    public static class PetQuery {
        @NotBlank
        private String id;
    }

    @Data
    public static class AdoptionRequestInput {
        @NotBlank
        private String petId; // foreign key as String

        @NotBlank
        @Size(max = 100)
        private String applicantName;

        @NotBlank
        @Pattern(regexp = "PENDING|APPROVED|REJECTED")
        private String status;
    }

    @Data
    public static class AdoptionRequestUpdate {
        @NotBlank
        private String id;

        @NotBlank
        private String petId;

        @NotBlank
        @Size(max = 100)
        private String applicantName;

        @NotBlank
        @Pattern(regexp = "PENDING|APPROVED|REJECTED")
        private String status;
    }

    @Data
    public static class AdoptionRequestQuery {
        @NotBlank
        private String id;
    }
}