package com.java_template.prototype;

import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

@Validated
@RestController
@RequestMapping(path = "/prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ConcurrentHashMap<String, List<Job>> jobCache = new ConcurrentHashMap<>();
    private final AtomicLong jobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<Pet>> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<AdoptionRequest>> adoptionRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    /*
     * --------------- JOB CRUD ---------------
     */

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody @Valid JobCreateDto jobDto) {
        logger.info("Received request to create Job: {}", jobDto);
        try {
            Job job = dtoToJob(jobDto);
            String id = addJob(job);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", id);
            resp.put("status", "Job created and processed");
            logger.info("Job created with id {}", id);
            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException e) {
            logger.error("Error creating Job: {}", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error creating Job", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    // GET example with @RequestParam - validate id not blank
    @GetMapping("/jobs")
    public ResponseEntity<Job> getJob(@RequestParam @NotBlank String id) {
        logger.info("Fetching Job with id: {}", id);
        Job job = getEntityById(jobCache, id, Job.class, "Job");
        return ResponseEntity.ok(job);
    }

    @PutMapping("/jobs")
    public ResponseEntity<Map<String, Object>> updateJob(@RequestBody @Valid JobUpdateDto jobDto) {
        logger.info("Updating Job: {}", jobDto);
        try {
            Job job = dtoToJob(jobDto);
            updateEntity(jobCache, job.getId(), job, Job.class, "Job");
            processJob(job);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", job.getId());
            resp.put("status", "Job updated and processed");
            logger.info("Job updated with id {}", job.getId());
            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException e) {
            logger.error("Error updating Job: {}", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error updating Job", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @DeleteMapping("/jobs")
    public ResponseEntity<Map<String, Object>> deleteJob(@RequestParam @NotBlank String id) {
        logger.info("Deleting Job with id: {}", id);
        boolean removed = deleteEntity(jobCache, id, Job.class, "Job");
        if (!removed) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Job deleted");
        logger.info("Deleted Job with id {}", id);
        return ResponseEntity.ok(resp);
    }

    /*
     * --------------- PET CRUD ---------------
     */

    @PostMapping("/pets")
    public ResponseEntity<Map<String, Object>> createPet(@RequestBody @Valid PetCreateDto petDto) {
        logger.info("Received request to create Pet: {}", petDto);
        try {
            Pet pet = dtoToPet(petDto);
            String id = addPet(pet);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", id);
            resp.put("status", "Pet created and processed");
            logger.info("Pet created with id {}", id);
            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException e) {
            logger.error("Error creating Pet: {}", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error creating Pet", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @GetMapping("/pets")
    public ResponseEntity<Pet> getPet(@RequestParam @NotBlank String id) {
        logger.info("Fetching Pet with id: {}", id);
        Pet pet = getEntityById(petCache, id, Pet.class, "Pet");
        return ResponseEntity.ok(pet);
    }

    @PutMapping("/pets")
    public ResponseEntity<Map<String, Object>> updatePet(@RequestBody @Valid PetUpdateDto petDto) {
        logger.info("Updating Pet: {}", petDto);
        try {
            Pet pet = dtoToPet(petDto);
            updateEntity(petCache, pet.getId(), pet, Pet.class, "Pet");
            processPet(pet);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", pet.getId());
            resp.put("status", "Pet updated and processed");
            logger.info("Pet updated with id {}", pet.getId());
            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException e) {
            logger.error("Error updating Pet: {}", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error updating Pet", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @DeleteMapping("/pets")
    public ResponseEntity<Map<String, Object>> deletePet(@RequestParam @NotBlank String id) {
        logger.info("Deleting Pet with id: {}", id);
        boolean removed = deleteEntity(petCache, id, Pet.class, "Pet");
        if (!removed) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Pet deleted");
        logger.info("Deleted Pet with id {}", id);
        return ResponseEntity.ok(resp);
    }

    /*
     * --------------- ADOPTIONREQUEST CRUD ---------------
     */

    @PostMapping("/adoptionRequests")
    public ResponseEntity<Map<String, Object>> createAdoptionRequest(@RequestBody @Valid AdoptionRequestCreateDto requestDto) {
        logger.info("Received request to create AdoptionRequest: {}", requestDto);
        try {
            AdoptionRequest request = dtoToAdoptionRequest(requestDto);
            String id = addAdoptionRequest(request);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", id);
            resp.put("status", "AdoptionRequest created and processed");
            logger.info("AdoptionRequest created with id {}", id);
            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException e) {
            logger.error("Error creating AdoptionRequest: {}", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error creating AdoptionRequest", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @GetMapping("/adoptionRequests")
    public ResponseEntity<AdoptionRequest> getAdoptionRequest(@RequestParam @NotBlank String id) {
        logger.info("Fetching AdoptionRequest with id: {}", id);
        AdoptionRequest request = getEntityById(adoptionRequestCache, id, AdoptionRequest.class, "AdoptionRequest");
        return ResponseEntity.ok(request);
    }

    @PutMapping("/adoptionRequests")
    public ResponseEntity<Map<String, Object>> updateAdoptionRequest(@RequestBody @Valid AdoptionRequestUpdateDto requestDto) {
        logger.info("Updating AdoptionRequest: {}", requestDto);
        try {
            AdoptionRequest request = dtoToAdoptionRequest(requestDto);
            updateEntity(adoptionRequestCache, request.getId(), request, AdoptionRequest.class, "AdoptionRequest");
            processAdoptionRequest(request);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", request.getId());
            resp.put("status", "AdoptionRequest updated and processed");
            logger.info("AdoptionRequest updated with id {}", request.getId());
            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException e) {
            logger.error("Error updating AdoptionRequest: {}", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error updating AdoptionRequest", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @DeleteMapping("/adoptionRequests")
    public ResponseEntity<Map<String, Object>> deleteAdoptionRequest(@RequestParam @NotBlank String id) {
        logger.info("Deleting AdoptionRequest with id: {}", id);
        boolean removed = deleteEntity(adoptionRequestCache, id, AdoptionRequest.class, "AdoptionRequest");
        if (!removed) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "AdoptionRequest deleted");
        logger.info("Deleted AdoptionRequest with id {}", id);
        return ResponseEntity.ok(resp);
    }

    /*
     * ====== COMMON CACHE OPERATIONS ======
     */

    private <T> T getEntityById(ConcurrentHashMap<String, List<T>> cache, String id, Class<T> clazz, String entityName) {
        List<T> entities = cache.get("entities");
        if (entities == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, entityName + " not found");
        }
        Optional<T> found = entities.stream()
                .filter(e -> {
                    try {
                        String eid = (String) clazz.getMethod("getId").invoke(e);
                        return eid.equals(id);
                    } catch (Exception ex) {
                        logger.error("Reflection error getting id for {}: {}", entityName, ex.getMessage());
                        return false;
                    }
                }).findFirst();
        if (found.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, entityName + " not found");
        }
        return found.get();
    }

    private <T> void updateEntity(ConcurrentHashMap<String, List<T>> cache, String id, T newEntity, Class<T> clazz, String entityName) {
        List<T> entities = cache.get("entities");
        if (entities == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, entityName + " not found");
        }
        boolean updated = false;
        for (int i = 0; i < entities.size(); i++) {
            T e = entities.get(i);
            try {
                String eid = (String) clazz.getMethod("getId").invoke(e);
                if (eid.equals(id)) {
                    Object oldTechId = clazz.getMethod("getTechnicalId").invoke(e);
                    Object newTechId = clazz.getMethod("getTechnicalId").invoke(newEntity);
                    if (newTechId == null && oldTechId != null) {
                        clazz.getMethod("setTechnicalId", UUID.class).invoke(newEntity, oldTechId);
                    }
                    clazz.getMethod("setId", String.class).invoke(newEntity, id);
                    entities.set(i, newEntity);
                    updated = true;
                    break;
                }
            } catch (Exception ex) {
                logger.error("Reflection error updating {}: {}", entityName, ex.getMessage());
                throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Reflection error");
            }
        }
        if (!updated) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, entityName + " not found");
        }
    }

    private <T> boolean deleteEntity(ConcurrentHashMap<String, List<T>> cache, String id, Class<T> clazz, String entityName) {
        List<T> entities = cache.get("entities");
        if (entities == null) return false;
        Iterator<T> it = entities.iterator();
        while (it.hasNext()) {
            T e = it.next();
            try {
                String eid = (String) clazz.getMethod("getId").invoke(e);
                if (eid.equals(id)) {
                    it.remove();
                    return true;
                }
            } catch (Exception ex) {
                logger.error("Reflection error deleting {}: {}", entityName, ex.getMessage());
                throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Reflection error");
            }
        }
        return false;
    }

    /*
     * ====== ADD ENTITY METHODS ======
     */

    private String addJob(Job job) {
        String id = String.valueOf(jobIdCounter.getAndIncrement());
        job.setId(id);
        if (job.getTechnicalId() == null) {
            job.setTechnicalId(UUID.randomUUID());
        }
        jobCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(job);
        processJob(job);
        return id;
    }

    private String addPet(Pet pet) {
        String id = String.valueOf(petIdCounter.getAndIncrement());
        pet.setId(id);
        if (pet.getTechnicalId() == null) {
            pet.setTechnicalId(UUID.randomUUID());
        }
        petCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(pet);
        processPet(pet);
        return id;
    }

    private String addAdoptionRequest(AdoptionRequest request) {
        String id = String.valueOf(adoptionRequestIdCounter.getAndIncrement());
        request.setId(id);
        if (request.getTechnicalId() == null) {
            request.setTechnicalId(UUID.randomUUID());
        }
        adoptionRequestCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(request);
        processAdoptionRequest(request);
        return id;
    }

    /*
     * ====== PROCESS ENTITY METHODS (Event simulation) ======
     */

    private void processJob(Job job) {
        // TODO: Replace with real event processing logic.
        logger.info("Processing Job event for job id: {}, name: {}", job.getId(), job.getName());
    }

    private void processPet(Pet pet) {
        // TODO: Replace with real event processing logic.
        logger.info("Processing Pet event for pet id: {}, name: {}", pet.getId(), pet.getName());
    }

    private void processAdoptionRequest(AdoptionRequest request) {
        // TODO: Replace with real event processing logic.
        logger.info("Processing AdoptionRequest event for id: {}, petId: {}, adopterId: {}",
                request.getId(), request.getPetId(), request.getAdopterId());
    }

    /*
     * ====== DTOs for requests ======
     * Use only primitives or String, no nested objects.
     */

    @Data
    public static class JobCreateDto {
        @NotBlank
        @Size(max = 100)
        private String name;

        @NotBlank
        @Size(max = 50)
        private String type;

        // Add other fields as String/primitives if needed, no nested objects
    }

    @Data
    public static class JobUpdateDto {
        @NotBlank
        private String id;

        @NotBlank
        @Size(max = 100)
        private String name;

        @NotBlank
        @Size(max = 50)
        private String type;
    }

    @Data
    public static class PetCreateDto {
        @NotBlank
        @Size(max = 100)
        private String name;

        @NotBlank
        @Size(max = 50)
        private String species;

        @NotBlank
        @Size(max = 50)
        private String breed;

        @NotNull
        private Integer age;

        @NotBlank
        private String ownerId; // UUID string of owner
    }

    @Data
    public static class PetUpdateDto {
        @NotBlank
        private String id;

        @NotBlank
        @Size(max = 100)
        private String name;

        @NotBlank
        @Size(max = 50)
        private String species;

        @NotBlank
        @Size(max = 50)
        private String breed;

        @NotNull
        private Integer age;

        @NotBlank
        private String ownerId;
    }

    @Data
    public static class AdoptionRequestCreateDto {
        @NotBlank
        private String petId;

        @NotBlank
        private String adopterId;

        @NotBlank
        @Size(max = 500)
        private String message;
    }

    @Data
    public static class AdoptionRequestUpdateDto {
        @NotBlank
        private String id;

        @NotBlank
        private String petId;

        @NotBlank
        private String adopterId;

        @NotBlank
        @Size(max = 500)
        private String message;
    }

    /*
     * ====== Helper DTO to Entity conversion methods ======
     */

    private Job dtoToJob(JobCreateDto dto) {
        Job job = new Job();
        job.setName(dto.getName());
        job.setType(dto.getType());
        job.setTechnicalId(UUID.randomUUID());
        return job;
    }

    private Job dtoToJob(JobUpdateDto dto) {
        Job job = new Job();
        job.setId(dto.getId());
        job.setName(dto.getName());
        job.setType(dto.getType());
        job.setTechnicalId(UUID.randomUUID());
        return job;
    }

    private Pet dtoToPet(PetCreateDto dto) {
        Pet pet = new Pet();
        pet.setName(dto.getName());
        pet.setSpecies(dto.getSpecies());
        pet.setBreed(dto.getBreed());
        pet.setAge(dto.getAge());
        pet.setOwnerId(dto.getOwnerId());
        pet.setTechnicalId(UUID.randomUUID());
        return pet;
    }

    private Pet dtoToPet(PetUpdateDto dto) {
        Pet pet = new Pet();
        pet.setId(dto.getId());
        pet.setName(dto.getName());
        pet.setSpecies(dto.getSpecies());
        pet.setBreed(dto.getBreed());
        pet.setAge(dto.getAge());
        pet.setOwnerId(dto.getOwnerId());
        pet.setTechnicalId(UUID.randomUUID());
        return pet;
    }

    private AdoptionRequest dtoToAdoptionRequest(AdoptionRequestCreateDto dto) {
        AdoptionRequest req = new AdoptionRequest();
        req.setPetId(dto.getPetId());
        req.setAdopterId(dto.getAdopterId());
        req.setMessage(dto.getMessage());
        req.setTechnicalId(UUID.randomUUID());
        return req;
    }

    private AdoptionRequest dtoToAdoptionRequest(AdoptionRequestUpdateDto dto) {
        AdoptionRequest req = new AdoptionRequest();
        req.setId(dto.getId());
        req.setPetId(dto.getPetId());
        req.setAdopterId(dto.getAdopterId());
        req.setMessage(dto.getMessage());
        req.setTechnicalId(UUID.randomUUID());
        return req;
    }
}