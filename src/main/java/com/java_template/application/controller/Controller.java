package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // POST /controller/jobs - create new Job entity
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody Map<String, String> request) throws ExecutionException, InterruptedException {
        String sourceUrl = request.get("sourceUrl");
        if (sourceUrl == null || sourceUrl.isBlank()) {
            log.error("Job creation failed: sourceUrl is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("sourceUrl is required and cannot be blank");
        }

        Job job = new Job();
        job.setSourceUrl(sourceUrl);
        job.setCreatedAt(LocalDateTime.now());
        job.setStatus(Job.JobStatusEnum.PENDING);

        if (!job.isValid()) {
            log.error("Job creation failed: validation failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid job data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("Job", ENTITY_VERSION, job);
        UUID technicalId = idFuture.get(); // wait for completion

        log.info("Created Job with technicalId: {}", technicalId);

        // Fetch the created Job with technicalId to pass for processing
        CompletableFuture<ObjectNode> jobFuture = entityService.getItem("Job", ENTITY_VERSION, technicalId);
        ObjectNode jobNode = jobFuture.get();
        Job createdJob = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .convertValue(jobNode, Job.class);
        createdJob.setTechnicalId(technicalId);

        // processJob(createdJob);  // Removed processing method call

        return ResponseEntity.status(HttpStatus.CREATED).body(createdJob);
    }

    // GET /controller/jobs/{id} - retrieve Job by id (technicalId)
    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getJob(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> jobFuture = entityService.getItem("Job", ENTITY_VERSION, id);
        ObjectNode jobNode = jobFuture.get();
        if (jobNode == null || jobNode.isEmpty()) {
            log.error("Job not found: technicalId {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        Job job = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .convertValue(jobNode, Job.class);
        job.setTechnicalId(id);
        return ResponseEntity.ok(job);
    }

    // POST /controller/pets - create new Pet entity
    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Map<String, Object> request) throws ExecutionException, InterruptedException {
        Object nameObj = request.get("name");
        Object categoryObj = request.get("category");
        Object statusObj = request.get("status");
        if (!(nameObj instanceof String) || ((String) nameObj).isBlank()) {
            log.error("Pet creation failed: name is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("name is required and cannot be blank");
        }
        if (!(categoryObj instanceof String) || ((String) categoryObj).isBlank()) {
            log.error("Pet creation failed: category is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("category is required and cannot be blank");
        }
        if (!(statusObj instanceof String) || ((String) statusObj).isBlank()) {
            log.error("Pet creation failed: status is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("status is required and cannot be blank");
        }

        Pet pet = new Pet();
        pet.setName((String) nameObj);
        pet.setCategory((String) categoryObj);

        Object photoUrlsObj = request.get("photoUrls");
        if (photoUrlsObj instanceof List<?>) {
            List<String> photoUrls = new ArrayList<>();
            for (Object o : (List<?>) photoUrlsObj) {
                if (o instanceof String) {
                    photoUrls.add((String) o);
                }
            }
            pet.setPhotoUrls(photoUrls);
        } else {
            pet.setPhotoUrls(Collections.emptyList());
        }

        Object tagsObj = request.get("tags");
        if (tagsObj instanceof List<?>) {
            List<String> tags = new ArrayList<>();
            for (Object o : (List<?>) tagsObj) {
                if (o instanceof String) {
                    tags.add((String) o);
                }
            }
            pet.setTags(tags);
        } else {
            pet.setTags(Collections.emptyList());
        }

        try {
            pet.setStatus(Pet.PetStatusEnum.valueOf(((String) statusObj).toUpperCase()));
        } catch (IllegalArgumentException e) {
            log.error("Pet creation failed: invalid status value '{}'", statusObj);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid status value");
        }

        pet.setCreatedAt(LocalDateTime.now());

        if (!pet.isValid()) {
            log.error("Pet creation failed: validation failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid pet data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();

        log.info("Created Pet with technicalId: {}", technicalId);

        CompletableFuture<ObjectNode> petFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
        ObjectNode petNode = petFuture.get();
        Pet createdPet = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .convertValue(petNode, Pet.class);
        createdPet.setTechnicalId(technicalId);

        // processPet(createdPet);  // Removed processing method call

        return ResponseEntity.status(HttpStatus.CREATED).body(createdPet);
    }

    // GET /controller/pets/{id} - retrieve Pet by id (technicalId)
    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> petFuture = entityService.getItem("Pet", ENTITY_VERSION, id);
        ObjectNode petNode = petFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            log.error("Pet not found: technicalId {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .convertValue(petNode, Pet.class);
        pet.setTechnicalId(id);
        return ResponseEntity.ok(pet);
    }

}