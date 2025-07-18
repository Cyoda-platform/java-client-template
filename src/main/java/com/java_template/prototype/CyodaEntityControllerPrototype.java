package com.java_template.prototype;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/cyoda/jobs")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    /*
     * --------------- JOB CRUD using EntityService ---------------
     */

    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody @Valid JobCreateDto jobDto) throws ExecutionException, InterruptedException {
        logger.info("Received request to create Job: {}", jobDto);
        Job job = dtoToJob(jobDto);
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Job",
                ENTITY_VERSION,
                job
        );
        UUID technicalId = idFuture.get();
        job.setTechnicalId(technicalId);
        String id = technicalId.toString();
        processJob(job);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Job created and processed");
        logger.info("Job created with technicalId {}", id);
        return ResponseEntity.ok(resp);
    }

    @GetMapping
    public ResponseEntity<Job> getJob(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Fetching Job with id: {}", id);
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Job", ENTITY_VERSION, technicalId);
        ObjectNode obj = itemFuture.get();
        if (obj == null || obj.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        Job job = convertObjectNodeToJob(obj);
        return ResponseEntity.ok(job);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateJob(@RequestBody @Valid JobUpdateDto jobDto) throws ExecutionException, InterruptedException {
        logger.info("Updating Job: {}", jobDto);
        Job job = dtoToJob(jobDto);
        UUID technicalId;
        try {
            technicalId = UUID.fromString(jobDto.getId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid UUID format for id");
        }
        job.setTechnicalId(technicalId);
        CompletableFuture<UUID> updatedItemId = entityService.updateItem(
                "Job",
                ENTITY_VERSION,
                technicalId,
                job
        );
        UUID updatedId = updatedItemId.get();
        processJob(job);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", updatedId.toString());
        resp.put("status", "Job updated and processed");
        logger.info("Job updated with technicalId {}", updatedId);
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteJob(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Deleting Job with id: {}", id);
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid UUID format for id");
        }
        CompletableFuture<UUID> deletedItemId = entityService.deleteItem(
                "Job",
                ENTITY_VERSION,
                technicalId
        );
        UUID deletedId = deletedItemId.get();
        if (!deletedId.equals(technicalId)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", deletedId.toString());
        resp.put("status", "Job deleted");
        logger.info("Deleted Job with technicalId {}", deletedId);
        return ResponseEntity.ok(resp);
    }

    /*
     * --------------- PET CRUD (kept local cache as minor entity) ---------------
     */

    private final Map<String, Pet> petCache = new HashMap<>();
    private long petIdCounter = 1;

    @PostMapping("/pets")
    public ResponseEntity<Map<String, Object>> createPet(@RequestBody @Valid PetCreateDto petDto) {
        logger.info("Received request to create Pet: {}", petDto);
        Pet pet = dtoToPet(petDto);
        String id = String.valueOf(petIdCounter++);
        pet.setId(id);
        if (pet.getTechnicalId() == null) {
            pet.setTechnicalId(UUID.randomUUID());
        }
        petCache.put(id, pet);
        processPet(pet);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Pet created and processed");
        logger.info("Pet created with id {}", id);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/pets")
    public ResponseEntity<Pet> getPet(@RequestParam @NotBlank String id) {
        logger.info("Fetching Pet with id: {}", id);
        Pet pet = petCache.get(id);
        if (pet == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @PutMapping("/pets")
    public ResponseEntity<Map<String, Object>> updatePet(@RequestBody @Valid PetUpdateDto petDto) {
        logger.info("Updating Pet: {}", petDto);
        Pet pet = dtoToPet(petDto);
        String id = petDto.getId();
        Pet existing = petCache.get(id);
        if (existing == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        pet.setId(id);
        if (pet.getTechnicalId() == null) {
            pet.setTechnicalId(existing.getTechnicalId());
        }
        petCache.put(id, pet);
        processPet(pet);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Pet updated and processed");
        logger.info("Pet updated with id {}", id);
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/pets")
    public ResponseEntity<Map<String, Object>> deletePet(@RequestParam @NotBlank String id) {
        logger.info("Deleting Pet with id: {}", id);
        Pet removed = petCache.remove(id);
        if (removed == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Pet deleted");
        logger.info("Deleted Pet with id {}", id);
        return ResponseEntity.ok(resp);
    }

    /*
     * --------------- ADOPTIONREQUEST CRUD (kept local cache as minor entity) ---------------
     */

    private final Map<String, AdoptionRequest> adoptionRequestCache = new HashMap<>();
    private long adoptionRequestIdCounter = 1;

    @PostMapping("/adoptionRequests")
    public ResponseEntity<Map<String, Object>> createAdoptionRequest(@RequestBody @Valid AdoptionRequestCreateDto requestDto) {
        logger.info("Received request to create AdoptionRequest: {}", requestDto);
        AdoptionRequest request = dtoToAdoptionRequest(requestDto);
        String id = String.valueOf(adoptionRequestIdCounter++);
        request.setId(id);
        if (request.getTechnicalId() == null) {
            request.setTechnicalId(UUID.randomUUID());
        }
        adoptionRequestCache.put(id, request);
        processAdoptionRequest(request);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "AdoptionRequest created and processed");
        logger.info("AdoptionRequest created with id {}", id);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/adoptionRequests")
    public ResponseEntity<AdoptionRequest> getAdoptionRequest(@RequestParam @NotBlank String id) {
        logger.info("Fetching AdoptionRequest with id: {}", id);
        AdoptionRequest request = adoptionRequestCache.get(id);
        if (request == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
        }
        return ResponseEntity.ok(request);
    }

    @PutMapping("/adoptionRequests")
    public ResponseEntity<Map<String, Object>> updateAdoptionRequest(@RequestBody @Valid AdoptionRequestUpdateDto requestDto) {
        logger.info("Updating AdoptionRequest: {}", requestDto);
        AdoptionRequest request = dtoToAdoptionRequest(requestDto);
        String id = requestDto.getId();
        AdoptionRequest existing = adoptionRequestCache.get(id);
        if (existing == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
        }
        request.setId(id);
        if (request.getTechnicalId() == null) {
            request.setTechnicalId(existing.getTechnicalId());
        }
        adoptionRequestCache.put(id, request);
        processAdoptionRequest(request);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "AdoptionRequest updated and processed");
        logger.info("AdoptionRequest updated with id {}", id);
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/adoptionRequests")
    public ResponseEntity<Map<String, Object>> deleteAdoptionRequest(@RequestParam @NotBlank String id) {
        logger.info("Deleting AdoptionRequest with id: {}", id);
        AdoptionRequest removed = adoptionRequestCache.remove(id);
        if (removed == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "AdoptionRequest deleted");
        logger.info("Deleted AdoptionRequest with id {}", id);
        return ResponseEntity.ok(resp);
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

    /*
     * ====== Convert ObjectNode to Job entity ======
     */

    private Job convertObjectNodeToJob(ObjectNode obj) {
        Job job = new Job();
        if (obj.hasNonNull("technicalId")) {
            job.setTechnicalId(UUID.fromString(obj.get("technicalId").asText()));
        }
        if (obj.hasNonNull("id")) {
            job.setId(obj.get("id").asText());
        }
        if (obj.hasNonNull("name")) {
            job.setName(obj.get("name").asText());
        }
        if (obj.hasNonNull("type")) {
            job.setType(obj.get("type").asText());
        }
        return job;
    }
}