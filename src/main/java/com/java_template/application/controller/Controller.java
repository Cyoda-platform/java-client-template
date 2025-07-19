package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String ENTITY_NAME_JOB = "PurrfectPetsJob";
    private static final String ENTITY_NAME_PET = "Pet";
    private static final String ENTITY_NAME_ADOPTION_REQUEST = "AdoptionRequest";

    // -------------------- PurrfectPetsJob Endpoints --------------------

    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@Valid @RequestBody PurrfectPetsJob job) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (job.getJobType() == null || job.getJobType().isBlank()) {
            log.error("Job creation failed: jobType is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("jobType is required");
        }

        job.setStatus("PENDING");
        job.setCreatedAt(Optional.ofNullable(job.getCreatedAt()).orElse(LocalDateTime.now()));

        // Add job via EntityService
        UUID technicalId = entityService.addItem(ENTITY_NAME_JOB, ENTITY_VERSION, job).get();

        // Retrieve stored job to assign business id fields
        ObjectNode storedJob = entityService.getItem(ENTITY_NAME_JOB, ENTITY_VERSION, technicalId).get();
        PurrfectPetsJob storedJobEntity = objectMapper.treeToValue(storedJob, PurrfectPetsJob.class);

        String businessId = "job-" + technicalId.toString();
        storedJobEntity.setId(businessId);
        storedJobEntity.setJobId(businessId);

        // After processing, create new version with updated status
        entityService.addItem(ENTITY_NAME_JOB, ENTITY_VERSION, storedJobEntity).get();

        log.info("Created PurrfectPetsJob with ID: {}", businessId);
        Map<String, String> response = new HashMap<>();
        response.put("jobId", businessId);
        response.put("status", storedJobEntity.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJob(@PathVariable String jobId) throws ExecutionException, InterruptedException, JsonProcessingException {
        // Search by condition on jobId field (business id)
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.jobId", "EQUALS", jobId));
        ArrayNode results = entityService.getItemsByCondition(ENTITY_NAME_JOB, ENTITY_VERSION, condition).get();

        if (results.isEmpty()) {
            log.error("Job not found: {}", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        ObjectNode jobNode = (ObjectNode) results.get(0);
        PurrfectPetsJob job = objectMapper.treeToValue(jobNode, PurrfectPetsJob.class);
        return ResponseEntity.ok(job);
    }

    // -------------------- Pet Endpoints --------------------

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@Valid @RequestBody Pet pet) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (pet.getName() == null || pet.getName().isBlank()
                || pet.getSpecies() == null || pet.getSpecies().isBlank()
                || pet.getBreed() == null || pet.getBreed().isBlank()
                || pet.getAge() == null) {
            log.error("Pet creation failed: Missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Missing required pet fields: name, species, breed, age");
        }

        pet.setStatus("AVAILABLE");

        UUID technicalId = entityService.addItem(ENTITY_NAME_PET, ENTITY_VERSION, pet).get();

        ObjectNode storedPet = entityService.getItem(ENTITY_NAME_PET, ENTITY_VERSION, technicalId).get();
        Pet storedPetEntity = objectMapper.treeToValue(storedPet, Pet.class);

        String businessId = "pet-" + technicalId.toString();
        storedPetEntity.setId(businessId);
        storedPetEntity.setPetId(businessId);

        entityService.addItem(ENTITY_NAME_PET, ENTITY_VERSION, storedPetEntity).get();

        log.info("Created Pet with ID: {}", businessId);
        Map<String, String> response = new HashMap<>();
        response.put("petId", businessId);
        response.put("status", storedPetEntity.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/pets/{petId}")
    public ResponseEntity<?> getPet(@PathVariable String petId) throws ExecutionException, InterruptedException, JsonProcessingException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.petId", "EQUALS", petId));
        ArrayNode results = entityService.getItemsByCondition(ENTITY_NAME_PET, ENTITY_VERSION, condition).get();

        if (results.isEmpty()) {
            log.error("Pet not found: {}", petId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        ObjectNode petNode = (ObjectNode) results.get(0);
        Pet pet = objectMapper.treeToValue(petNode, Pet.class);
        return ResponseEntity.ok(pet);
    }

    // -------------------- AdoptionRequest Endpoints --------------------

    @PostMapping("/adoption-requests")
    public ResponseEntity<?> createAdoptionRequest(@Valid @RequestBody AdoptionRequest request) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (request.getPetId() == null || request.getPetId().isBlank()
                || request.getAdopterName() == null || request.getAdopterName().isBlank()) {
            log.error("Adoption request creation failed: Missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Missing required fields: petId, adopterName");
        }

        request.setStatus("PENDING");
        request.setRequestDate(Optional.ofNullable(request.getRequestDate()).orElse(LocalDateTime.now()));

        UUID technicalId = entityService.addItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, request).get();

        ObjectNode storedRequest = entityService.getItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, technicalId).get();
        AdoptionRequest storedRequestEntity = objectMapper.treeToValue(storedRequest, AdoptionRequest.class);

        String businessId = "request-" + technicalId.toString();
        storedRequestEntity.setId(businessId);
        storedRequestEntity.setRequestId(businessId);

        entityService.addItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, storedRequestEntity).get();

        log.info("Created AdoptionRequest with ID: {}", businessId);
        Map<String, String> response = new HashMap<>();
        response.put("requestId", businessId);
        response.put("status", storedRequestEntity.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/adoption-requests/{requestId}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String requestId) throws ExecutionException, InterruptedException, JsonProcessingException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.requestId", "EQUALS", requestId));
        ArrayNode results = entityService.getItemsByCondition(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, condition).get();

        if (results.isEmpty()) {
            log.error("Adoption request not found: {}", requestId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Adoption request not found");
        }
        ObjectNode requestNode = (ObjectNode) results.get(0);
        AdoptionRequest request = objectMapper.treeToValue(requestNode, AdoptionRequest.class);
        return ResponseEntity.ok(request);
    }

}