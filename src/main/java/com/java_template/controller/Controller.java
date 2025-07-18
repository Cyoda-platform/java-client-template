package com.java_template.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
import com.java_template.application.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/prototype/entity")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // ====== JOB CRUD ======

    @PostMapping("/job")
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody @Valid JobInput jobInput) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Received request to create Job: {}", jobInput);
        Job job = new Job();
        job.setId(null); // id is generated on add
        job.setTechnicalId(null);
        job.setStatus(jobInput.getStatus());
        job.setDescription(jobInput.getDescription());
        UUID id = entityService.addItem("job", ENTITY_VERSION, job).get();
        logger.info("Job created with id {}", id);
        return ResponseEntity.ok(Map.of("id", id.toString(), "status", "Job created and processed"));
    }

    @GetMapping("/job")
    public ResponseEntity<Job> getJob(@Valid @ModelAttribute JobQuery query) throws ExecutionException, InterruptedException, JsonProcessingException {
        String id = query.getId();
        logger.info("Received request to get Job with id: {}", id);
        UUID technicalId = UUID.fromString(id);
        ObjectNode node = entityService.getItem("job", ENTITY_VERSION, technicalId).get();
        if (node == null) {
            logger.error("Job with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        Job job = objectMapper.treeToValue(node, Job.class);
        return ResponseEntity.ok(job);
    }

    @PutMapping("/job")
    public ResponseEntity<Map<String, Object>> updateJob(@RequestBody @Valid JobUpdate jobUpdate) throws ExecutionException, InterruptedException, JsonProcessingException {
        String id = jobUpdate.getId();
        logger.info("Received request to update Job with id {}: {}", id, jobUpdate);
        Job job = new Job();
        job.setTechnicalId(UUID.fromString(id));
        job.setStatus(jobUpdate.getStatus());
        job.setDescription(jobUpdate.getDescription());
        UUID jobId = entityService.updateItem("job", ENTITY_VERSION, job).get();
        return ResponseEntity.ok(Map.of("id", jobId.toString(), "status", "Job updated and processed"));
    }

    @DeleteMapping("/job")
    public ResponseEntity<Map<String, Object>> deleteJob(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Received request to delete Job with id {}", id);
        UUID technicalId = UUID.fromString(id);
        UUID deletedId = entityService.deleteItem("job", ENTITY_VERSION, technicalId).get();
        if (deletedId == null) {
            logger.error("Job delete failed: Job not found");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        return ResponseEntity.ok(Map.of("id", deletedId.toString(), "status", "Job deleted"));
    }

    // ====== PET CRUD ======

    @PostMapping("/pet")
    public ResponseEntity<Map<String, Object>> createPet(@RequestBody @Valid PetInput petInput) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Received request to create Pet: {}", petInput);
        Pet pet = new Pet();
        pet.setId(null);
        pet.setTechnicalId(null);
        pet.setName(petInput.getName());
        pet.setType(petInput.getType());
        pet.setStatus(null); // no status in DTO input
        UUID id = entityService.addItem("pet", ENTITY_VERSION, pet).get();
        logger.info("Pet created with id {}", id);
        return ResponseEntity.ok(Map.of("id", id.toString(), "status", "Pet created and processed"));
    }

    @GetMapping("/pet")
    public ResponseEntity<Pet> getPet(@Valid @ModelAttribute PetQuery query) throws ExecutionException, InterruptedException, JsonProcessingException {
        String id = query.getId();
        logger.info("Received request to get Pet with id: {}", id);
        UUID technicalId = UUID.fromString(id);
        ObjectNode node = entityService.getItem("pet", ENTITY_VERSION, technicalId).get();
        if (node == null) {
            logger.error("Pet with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        Pet pet = objectMapper.treeToValue(node, Pet.class);
        return ResponseEntity.ok(pet);
    }

    @PutMapping("/pet")
    public ResponseEntity<Map<String, Object>> updatePet(@RequestBody @Valid PetUpdate petUpdate) throws ExecutionException, InterruptedException, JsonProcessingException {
        String id = petUpdate.getId();
        logger.info("Received request to update Pet with id {}: {}", id, petUpdate);
        Pet pet = new Pet();
        pet.setTechnicalId(UUID.fromString(id));
        pet.setName(petUpdate.getName());
        pet.setType(petUpdate.getType());
        pet.setStatus(null); // no status in DTO update input
        UUID petId = entityService.updateItem("pet", ENTITY_VERSION, pet).get();
        return ResponseEntity.ok(Map.of("id", petId.toString(), "status", "Pet updated and processed"));
    }

    @DeleteMapping("/pet")
    public ResponseEntity<Map<String, Object>> deletePet(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Received request to delete Pet with id {}", id);
        UUID technicalId = UUID.fromString(id);
        UUID deletedId = entityService.deleteItem("pet", ENTITY_VERSION, technicalId).get();
        if (deletedId == null) {
            logger.error("Pet delete failed: Pet not found");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(Map.of("id", deletedId.toString(), "status", "Pet deleted"));
    }

    // ====== ADOPTION REQUEST CRUD ======

    @PostMapping("/adoptionRequest")
    public ResponseEntity<Map<String, Object>> createAdoptionRequest(@RequestBody @Valid AdoptionRequestInput input) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Received request to create AdoptionRequest: {}", input);
        AdoptionRequest request = new AdoptionRequest();
        request.setId(null);
        request.setTechnicalId(null);
        request.setPetId(input.getPetId());
        request.setUserId(null); // no userId in DTO input
        request.setStatus(input.getStatus());
        UUID id = entityService.addItem("adoptionRequest", ENTITY_VERSION, request).get();
        logger.info("AdoptionRequest created with id {}", id);
        return ResponseEntity.ok(Map.of("id", id.toString(), "status", "AdoptionRequest created and processed"));
    }

    @GetMapping("/adoptionRequest")
    public ResponseEntity<AdoptionRequest> getAdoptionRequest(@Valid @ModelAttribute AdoptionRequestQuery query) throws ExecutionException, InterruptedException, JsonProcessingException {
        String id = query.getId();
        logger.info("Received request to get AdoptionRequest with id: {}", id);
        UUID technicalId = UUID.fromString(id);
        ObjectNode node = entityService.getItem("adoptionRequest", ENTITY_VERSION, technicalId).get();
        if (node == null) {
            logger.error("AdoptionRequest with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
        }
        AdoptionRequest request = objectMapper.treeToValue(node, AdoptionRequest.class);
        return ResponseEntity.ok(request);
    }

    @PutMapping("/adoptionRequest")
    public ResponseEntity<Map<String, Object>> updateAdoptionRequest(@RequestBody @Valid AdoptionRequestUpdate input) throws ExecutionException, InterruptedException, JsonProcessingException {
        String id = input.getId();
        logger.info("Received request to update AdoptionRequest with id {}: {}", id, input);
        AdoptionRequest request = new AdoptionRequest();
        request.setTechnicalId(UUID.fromString(id));
        request.setPetId(input.getPetId());
        request.setUserId(null); // no userId in DTO update input
        request.setStatus(input.getStatus());
        UUID requestId = entityService.updateItem("adoptionRequest", ENTITY_VERSION, request).get();
        return ResponseEntity.ok(Map.of("id", requestId.toString(), "status", "AdoptionRequest updated and processed"));
    }

    @DeleteMapping("/adoptionRequest")
    public ResponseEntity<Map<String, Object>> deleteAdoptionRequest(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Received request to delete AdoptionRequest with id {}", id);
        UUID technicalId = UUID.fromString(id);
        UUID deletedId = entityService.deleteItem("adoptionRequest", ENTITY_VERSION, technicalId).get();
        if (deletedId == null) {
            logger.error("AdoptionRequest delete failed: AdoptionRequest not found");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
        }
        return ResponseEntity.ok(Map.of("id", deletedId.toString(), "status", "AdoptionRequest deleted"));
    }

    // ====== DTO Classes for Validation ======

    public static class JobInput {
        @NotBlank
        private String status;

        private String description;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class JobUpdate extends JobInput {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class JobQuery {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class PetInput {
        @NotBlank
        private String name;

        @NotBlank
        private String type;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    public static class PetUpdate extends PetInput {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class PetQuery {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class AdoptionRequestInput {
        @NotBlank
        private String petId;

        @NotBlank
        private String status;

        public String getPetId() { return petId; }
        public void setPetId(String petId) { this.petId = petId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class AdoptionRequestUpdate extends AdoptionRequestInput {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class AdoptionRequestQuery {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

}