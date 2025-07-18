package com.java_template.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Job;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Validated
@RestController
@RequestMapping(path = "/jobs")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String ENTITY_NAME_JOB = "job";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<CreateResponse> createJob(@RequestBody @Valid JobCreateUpdateDTO jobDto) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Received request to create Job: {}", jobDto);
        Job job = toJob(jobDto);
        if (!job.isValid()) {
            logger.error("Invalid Job entity received");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Job entity");
        }
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME_JOB,
                ENTITY_VERSION,
                job);
        UUID technicalId = idFuture.get();
        job.setTechnicalId(technicalId);
        logger.info("Job created with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateResponse(technicalId.toString(), "Job created and processed"));
    }

    @GetMapping
    public ResponseEntity<List<Job>> listJobs() throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Listing all Jobs");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME_JOB, ENTITY_VERSION);
        ArrayNode items = itemsFuture.get();

        List<Job> jobList = items.findValuesAsText("technicalId").isEmpty() ? List.of() :
                items.findValuesAsText("technicalId").stream().map(id -> {
                    try {
                        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME_JOB, ENTITY_VERSION, UUID.fromString(id));
                        ObjectNode obj = itemFuture.get();
                        Job job = objectMapper.treeToValue(obj, Job.class);
                        job.setTechnicalId(UUID.fromString(id));
                        return job;
                    } catch (JsonProcessingException | InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }).toList();
        return ResponseEntity.ok(jobList);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Job> getJob(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Fetching Job with technicalId: {}", id);
        UUID uuid = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME_JOB, ENTITY_VERSION, uuid);
        ObjectNode obj = itemFuture.get();
        if (obj == null || obj.isEmpty()) {
            logger.error("Job not found with technicalId: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        Job job = objectMapper.treeToValue(obj, Job.class);
        job.setTechnicalId(uuid);
        return ResponseEntity.ok(job);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CreateResponse> updateJob(@PathVariable @NotBlank String id,
                                                    @RequestBody @Valid JobCreateUpdateDTO jobDto) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Updating Job with technicalId: {}", id);
        UUID uuid = UUID.fromString(id);
        Job job = toJob(jobDto);
        if (!job.isValid()) {
            logger.error("Invalid Job entity received for update");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Job entity");
        }
        job.setTechnicalId(uuid);
        CompletableFuture<UUID> updatedItemId = entityService.updateItem(
                ENTITY_NAME_JOB,
                ENTITY_VERSION,
                uuid,
                job);
        UUID updatedId = updatedItemId.get();
        if (!updatedId.equals(uuid)) {
            logger.error("Job update failed or not found for technicalId: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        return ResponseEntity.ok(new CreateResponse(id, "Job updated and processed"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<CreateResponse> deleteJob(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Deleting Job with technicalId: {}", id);
        UUID uuid = UUID.fromString(id);
        CompletableFuture<UUID> deletedItemId = entityService.deleteItem(
                ENTITY_NAME_JOB,
                ENTITY_VERSION,
                uuid);
        UUID deletedId = deletedItemId.get();
        if (!deletedId.equals(uuid)) {
            logger.error("Job not found for deletion with technicalId: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        return ResponseEntity.ok(new CreateResponse(id, "Job deleted"));
    }

    // DTO classes for validation & mapping

    public static class JobCreateUpdateDTO {
        @NotBlank
        @jakarta.validation.constraints.Size(max = 255)
        private String id; // business ID from entity

        @NotBlank
        @jakarta.validation.constraints.Size(max = 255)
        private String status;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return "JobCreateUpdateDTO{" +
                    "id='" + id + '\'' +
                    ", status='" + status + '\'' +
                    '}';
        }
    }

    private Job toJob(JobCreateUpdateDTO dto) {
        Job job = new Job();
        job.setId(dto.getId());
        job.setStatus(dto.getStatus());
        return job;
    }

    private static class CreateResponse {
        private final String entityId;
        private final String status;

        public CreateResponse(String entityId, String status) {
            this.entityId = entityId;
            this.status = status;
        }

        public String getEntityId() {
            return entityId;
        }

        public String getStatus() {
            return status;
        }
    }
}