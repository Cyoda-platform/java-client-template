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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/prototype/job")
@Validated
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    // ====== JOB CRUD ======

    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody @Valid JobInput jobInput) {
        logger.info("Received request to create Job: {}", jobInput);
        Job job = convertToJob(jobInput);
        if (job.getId() != null && !job.getId().isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "New Job cannot have an id");
        }
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Job",
                ENTITY_VERSION,
                job
        );
        UUID technicalId = idFuture.join();
        job.setTechnicalId(technicalId);
        logger.info("Job created with technicalId {}", technicalId);
        processJob(job);
        return ResponseEntity.ok(Map.of("id", job.getId(), "status", "Job created and processed"));
    }

    @GetMapping
    public ResponseEntity<Job> getJob(@Valid @ModelAttribute JobQuery query) {
        String id = query.getId();
        logger.info("Received request to get Job with id: {}", id);
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id)
        );
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("Job", ENTITY_VERSION, condition);
        ArrayNode items = itemsFuture.join();
        if (items.isEmpty()) {
            logger.error("Job with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        ObjectNode node = (ObjectNode) items.get(0);
        Job job = nodeToJob(node);
        return ResponseEntity.ok(job);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateJob(@RequestBody @Valid JobUpdate jobUpdate) {
        String id = jobUpdate.getId();
        logger.info("Received request to update Job with id {}: {}", id, jobUpdate);
        Job job = convertToJob(jobUpdate);

        // Retrieve existing entity to get technicalId
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id)
        );
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("Job", ENTITY_VERSION, condition);
        ArrayNode items = itemsFuture.join();
        if (items.isEmpty()) {
            logger.error("Job with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        ObjectNode existingNode = (ObjectNode) items.get(0);
        UUID technicalId = UUID.fromString(existingNode.get("technicalId").asText());
        job.setTechnicalId(technicalId);
        CompletableFuture<UUID> updatedItemId = entityService.updateItem("Job", ENTITY_VERSION, technicalId, job);
        updatedItemId.join();
        processJob(job);
        return ResponseEntity.ok(Map.of("id", id, "status", "Job updated and processed"));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteJob(@RequestParam @NotBlank String id) {
        logger.info("Received request to delete Job with id {}", id);
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id)
        );
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("Job", ENTITY_VERSION, condition);
        ArrayNode items = itemsFuture.join();
        if (items.isEmpty()) {
            logger.error("Job with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        ObjectNode existingNode = (ObjectNode) items.get(0);
        UUID technicalId = UUID.fromString(existingNode.get("technicalId").asText());
        CompletableFuture<UUID> deletedItemId = entityService.deleteItem("Job", ENTITY_VERSION, technicalId);
        deletedItemId.join();
        return ResponseEntity.ok(Map.of("id", id, "status", "Job deleted"));
    }

    // ====== PET CRUD (kept local cache as minor entity, no refactor) ======

    private final Map<String, List<Pet>> petCache = new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicLong petIdCounter = new java.util.concurrent.atomic.AtomicLong(1);

    @PostMapping(path = "/pet")
    public ResponseEntity<Map<String, Object>> createPet(@RequestBody @Valid PetInput petInput) {
        logger.info("Received request to create Pet: {}", petInput);
        Pet pet = convertToPet(petInput);
        if (pet.getId() != null && !pet.getId().isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "New Pet cannot have an id");
        }
        String id = String.valueOf(petIdCounter.getAndIncrement());
        pet.setId(id);
        pet.setTechnicalId(UUID.randomUUID());
        petCache.computeIfAbsent("entities", k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>())).add(pet);
        logger.info("Pet added to cache: {}", pet);
        processPet(pet);
        return ResponseEntity.ok(Map.of("id", id, "status", "Pet created and processed"));
    }

    @GetMapping(path = "/pet")
    public ResponseEntity<Pet> getPet(@Valid @ModelAttribute PetQuery query) {
        String id = query.getId();
        logger.info("Received request to get Pet with id: {}", id);
        List<Pet> entities = petCache.get("entities");
        if (entities == null) {
            logger.error("Pet with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        Optional<Pet> petOpt = entities.stream().filter(e -> id.equals(e.getId())).findFirst();
        if (petOpt.isEmpty()) {
            logger.error("Pet with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(petOpt.get());
    }

    @PutMapping(path = "/pet")
    public ResponseEntity<Map<String, Object>> updatePet(@RequestBody @Valid PetUpdate petUpdate) {
        String id = petUpdate.getId();
        logger.info("Received request to update Pet with id {}: {}", id, petUpdate);
        Pet pet = convertToPet(petUpdate);
        List<Pet> entities = petCache.get("entities");
        if (entities == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        synchronized (entities) {
            boolean found = false;
            for (int i = 0; i < entities.size(); i++) {
                if (id.equals(entities.get(i).getId())) {
                    pet.setId(id);
                    pet.setTechnicalId(entities.get(i).getTechnicalId());
                    entities.set(i, pet);
                    found = true;
                    logger.info("Pet with id {} updated in cache", id);
                    break;
                }
            }
            if (!found) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
            }
        }
        processPet(pet);
        return ResponseEntity.ok(Map.of("id", id, "status", "Pet updated and processed"));
    }

    @DeleteMapping(path = "/pet")
    public ResponseEntity<Map<String, Object>> deletePet(@RequestParam @NotBlank String id) {
        logger.info("Received request to delete Pet with id {}", id);
        List<Pet> entities = petCache.get("entities");
        if (entities == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        synchronized (entities) {
            boolean removed = entities.removeIf(e -> id.equals(e.getId()));
            if (!removed) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
            }
            logger.info("Pet with id {} removed from cache", id);
        }
        return ResponseEntity.ok(Map.of("id", id, "status", "Pet deleted"));
    }

    // ====== ADOPTION REQUEST CRUD (kept local cache as minor entity, no refactor) ======

    private final Map<String, List<AdoptionRequest>> adoptionRequestCache = new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicLong adoptionRequestIdCounter = new java.util.concurrent.atomic.AtomicLong(1);

    @PostMapping(path = "/adoptionRequest")
    public ResponseEntity<Map<String, Object>> createAdoptionRequest(@RequestBody @Valid AdoptionRequestInput input) {
        logger.info("Received request to create AdoptionRequest: {}", input);
        AdoptionRequest request = convertToAdoptionRequest(input);
        if (request.getId() != null && !request.getId().isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "New AdoptionRequest cannot have an id");
        }
        String id = String.valueOf(adoptionRequestIdCounter.getAndIncrement());
        request.setId(id);
        request.setTechnicalId(UUID.randomUUID());
        adoptionRequestCache.computeIfAbsent("entities", k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>())).add(request);
        logger.info("AdoptionRequest added to cache: {}", request);
        processAdoptionRequest(request);
        return ResponseEntity.ok(Map.of("id", id, "status", "AdoptionRequest created and processed"));
    }

    @GetMapping(path = "/adoptionRequest")
    public ResponseEntity<AdoptionRequest> getAdoptionRequest(@Valid @ModelAttribute AdoptionRequestQuery query) {
        String id = query.getId();
        logger.info("Received request to get AdoptionRequest with id: {}", id);
        List<AdoptionRequest> entities = adoptionRequestCache.get("entities");
        if (entities == null) {
            logger.error("AdoptionRequest with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
        }
        Optional<AdoptionRequest> requestOpt = entities.stream().filter(e -> id.equals(e.getId())).findFirst();
        if (requestOpt.isEmpty()) {
            logger.error("AdoptionRequest with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
        }
        return ResponseEntity.ok(requestOpt.get());
    }

    @PutMapping(path = "/adoptionRequest")
    public ResponseEntity<Map<String, Object>> updateAdoptionRequest(@RequestBody @Valid AdoptionRequestUpdate input) {
        String id = input.getId();
        logger.info("Received request to update AdoptionRequest with id {}: {}", id, input);
        AdoptionRequest request = convertToAdoptionRequest(input);
        List<AdoptionRequest> entities = adoptionRequestCache.get("entities");
        if (entities == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
        }
        synchronized (entities) {
            boolean found = false;
            for (int i = 0; i < entities.size(); i++) {
                if (id.equals(entities.get(i).getId())) {
                    request.setId(id);
                    request.setTechnicalId(entities.get(i).getTechnicalId());
                    entities.set(i, request);
                    found = true;
                    logger.info("AdoptionRequest with id {} updated in cache", id);
                    break;
                }
            }
            if (!found) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
            }
        }
        processAdoptionRequest(request);
        return ResponseEntity.ok(Map.of("id", id, "status", "AdoptionRequest updated and processed"));
    }

    @DeleteMapping(path = "/adoptionRequest")
    public ResponseEntity<Map<String, Object>> deleteAdoptionRequest(@RequestParam @NotBlank String id) {
        logger.info("Received request to delete AdoptionRequest with id {}", id);
        List<AdoptionRequest> entities = adoptionRequestCache.get("entities");
        if (entities == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
        }
        synchronized (entities) {
            boolean removed = entities.removeIf(e -> id.equals(e.getId()));
            if (!removed) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
            }
            logger.info("AdoptionRequest with id {} removed from cache", id);
        }
        return ResponseEntity.ok(Map.of("id", id, "status", "AdoptionRequest deleted"));
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

    // ====== Conversion Utilities ======

    private Job convertToJob(JobInput input) {
        Job job = new Job();
        job.setId(null);
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

    private Job nodeToJob(ObjectNode node) {
        Job job = new Job();
        job.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        job.setId(node.hasNonNull("id") ? node.get("id").asText() : null);
        job.setName(node.hasNonNull("name") ? node.get("name").asText() : null);
        job.setDescription(node.hasNonNull("description") ? node.get("description").asText() : null);
        job.setStatus(node.hasNonNull("status") ? node.get("status").asText() : null);
        return job;
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
        private String petId;

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