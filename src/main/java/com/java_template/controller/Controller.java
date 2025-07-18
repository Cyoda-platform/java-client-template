package com.java_template.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
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
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody @Valid JobInput jobInput) throws ExecutionException, InterruptedException {
        logger.info("Received request to create Job: {}", jobInput);
        try {
            Job job = objectMapper.convertValue(jobInput, Job.class);
            job.setId(null); // id is generated on add
            job.setTechnicalId(null);
            UUID id = entityService.addItem("job", ENTITY_VERSION, job).get();
            logger.info("Job created with id {}", id);
            return ResponseEntity.ok(Map.of("id", id.toString(), "status", "Job created and processed"));
        } catch (JsonProcessingException e) {
            logger.error("Error mapping JobInput to Job", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Job input");
        }
    }

    @GetMapping("/job")
    public ResponseEntity<Job> getJob(@Valid @ModelAttribute JobQuery query) throws ExecutionException, InterruptedException {
        String id = query.getId();
        logger.info("Received request to get Job with id: {}", id);
        Job job = entityService.searchEntityById("job", ENTITY_VERSION, id, Job.class);
        if (job == null) {
            logger.error("Job with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        return ResponseEntity.ok(job);
    }

    @PutMapping("/job")
    public ResponseEntity<Map<String, Object>> updateJob(@RequestBody @Valid JobUpdate jobUpdate) throws ExecutionException, InterruptedException {
        String id = jobUpdate.getId();
        logger.info("Received request to update Job with id {}: {}", id, jobUpdate);
        try {
            Job job = objectMapper.convertValue(jobUpdate, Job.class);
            UUID jobId = entityService.addItem("job", ENTITY_VERSION, job).get();
            return ResponseEntity.ok(Map.of("id", jobId.toString(), "status", "Job updated and processed"));
        } catch (JsonProcessingException e) {
            logger.error("Error mapping JobUpdate to Job", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Job update input");
        }
    }

    @DeleteMapping("/job")
    public ResponseEntity<Map<String, Object>> deleteJob(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Received request to delete Job with id {}", id);
        boolean deleted = entityService.deleteEntityById("job", ENTITY_VERSION, id);
        if (!deleted) {
            logger.error("Job delete failed: Job not found");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        return ResponseEntity.ok(Map.of("id", id, "status", "Job deleted"));
    }

    // ====== PET CRUD ======

    @PostMapping("/pet")
    public ResponseEntity<Map<String, Object>> createPet(@RequestBody @Valid PetInput petInput) throws ExecutionException, InterruptedException {
        logger.info("Received request to create Pet: {}", petInput);
        try {
            Pet pet = objectMapper.convertValue(petInput, Pet.class);
            pet.setId(null);
            pet.setTechnicalId(null);
            UUID id = entityService.addItem("pet", ENTITY_VERSION, pet).get();
            logger.info("Pet created with id {}", id);
            return ResponseEntity.ok(Map.of("id", id.toString(), "status", "Pet created and processed"));
        } catch (JsonProcessingException e) {
            logger.error("Error mapping PetInput to Pet", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Pet input");
        }
    }

    @GetMapping("/pet")
    public ResponseEntity<Pet> getPet(@Valid @ModelAttribute PetQuery query) throws ExecutionException, InterruptedException {
        String id = query.getId();
        logger.info("Received request to get Pet with id: {}", id);
        Pet pet = entityService.searchEntityById("pet", ENTITY_VERSION, id, Pet.class);
        if (pet == null) {
            logger.error("Pet with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @PutMapping("/pet")
    public ResponseEntity<Map<String, Object>> updatePet(@RequestBody @Valid PetUpdate petUpdate) throws ExecutionException, InterruptedException {
        String id = petUpdate.getId();
        logger.info("Received request to update Pet with id {}: {}", id, petUpdate);
        try {
            Pet pet = objectMapper.convertValue(petUpdate, Pet.class);
            UUID petId = entityService.addItem("pet", ENTITY_VERSION, pet).get();
            return ResponseEntity.ok(Map.of("id", petId.toString(), "status", "Pet updated and processed"));
        } catch (JsonProcessingException e) {
            logger.error("Error mapping PetUpdate to Pet", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Pet update input");
        }
    }

    @DeleteMapping("/pet")
    public ResponseEntity<Map<String, Object>> deletePet(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Received request to delete Pet with id {}", id);
        boolean deleted = entityService.deleteEntityById("pet", ENTITY_VERSION, id);
        if (!deleted) {
            logger.error("Pet delete failed: Pet not found");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(Map.of("id", id, "status", "Pet deleted"));
    }

    // ====== ADOPTION REQUEST CRUD ======

    @PostMapping("/adoptionRequest")
    public ResponseEntity<Map<String, Object>> createAdoptionRequest(@RequestBody @Valid AdoptionRequestInput input) throws ExecutionException, InterruptedException {
        logger.info("Received request to create AdoptionRequest: {}", input);
        try {
            AdoptionRequest request = objectMapper.convertValue(input, AdoptionRequest.class);
            request.setId(null);
            request.setTechnicalId(null);
            UUID id = entityService.addItem("adoptionRequest", ENTITY_VERSION, request).get();
            logger.info("AdoptionRequest created with id {}", id);
            return ResponseEntity.ok(Map.of("id", id.toString(), "status", "AdoptionRequest created and processed"));
        } catch (JsonProcessingException e) {
            logger.error("Error mapping AdoptionRequestInput to AdoptionRequest", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid AdoptionRequest input");
        }
    }

    @GetMapping("/adoptionRequest")
    public ResponseEntity<AdoptionRequest> getAdoptionRequest(@Valid @ModelAttribute AdoptionRequestQuery query) throws ExecutionException, InterruptedException {
        String id = query.getId();
        logger.info("Received request to get AdoptionRequest with id: {}", id);
        AdoptionRequest request = entityService.searchEntityById("adoptionRequest", ENTITY_VERSION, id, AdoptionRequest.class);
        if (request == null) {
            logger.error("AdoptionRequest with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
        }
        return ResponseEntity.ok(request);
    }

    @PutMapping("/adoptionRequest")
    public ResponseEntity<Map<String, Object>> updateAdoptionRequest(@RequestBody @Valid AdoptionRequestUpdate input) throws ExecutionException, InterruptedException {
        String id = input.getId();
        logger.info("Received request to update AdoptionRequest with id {}: {}", id, input);
        try {
            AdoptionRequest request = objectMapper.convertValue(input, AdoptionRequest.class);
            UUID requestId = entityService.addItem("adoptionRequest", ENTITY_VERSION, request).get();
            return ResponseEntity.ok(Map.of("id", requestId.toString(), "status", "AdoptionRequest updated and processed"));
        } catch (JsonProcessingException e) {
            logger.error("Error mapping AdoptionRequestUpdate to AdoptionRequest", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid AdoptionRequest update input");
        }
    }

    @DeleteMapping("/adoptionRequest")
    public ResponseEntity<Map<String, Object>> deleteAdoptionRequest(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Received request to delete AdoptionRequest with id {}", id);
        boolean deleted = entityService.deleteEntityById("adoptionRequest", ENTITY_VERSION, id);
        if (!deleted) {
            logger.error("AdoptionRequest delete failed: AdoptionRequest not found");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found");
        }
        return ResponseEntity.ok(Map.of("id", id, "status", "AdoptionRequest deleted"));
    }

    // ====== DTO Classes for Validation ======

    public static class JobInput {
        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 255)
        private String description;

        @NotBlank
        @Pattern(regexp = "NEW|RUNNING|COMPLETED|FAILED")
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
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
        @Size(max = 50)
        private String name;

        @NotBlank
        @Size(max = 30)
        private String type;

        @NotNull
        private Integer age;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
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
        @Size(max = 100)
        private String applicantName;

        @NotBlank
        @Pattern(regexp = "PENDING|APPROVED|REJECTED")
        private String status;

        public String getPetId() { return petId; }
        public void setPetId(String petId) { this.petId = petId; }
        public String getApplicantName() { return applicantName; }
        public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
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