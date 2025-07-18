package com.java_template.prototype;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.EmailDispatch;
import com.java_template.application.entity.Job;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/jobs")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private static final String ENTITY_NAME_JOB = "Job";

    // ======== JOB CRUD ========

    @PostMapping
    public ResponseEntity<CreateResponse> createJob(@RequestBody @Valid JobCreateUpdateDTO jobDto) throws Exception {
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
        processJob(job);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateResponse(technicalId.toString(), "Job created and processed"));
    }

    @GetMapping
    public ResponseEntity<List<Job>> listJobs() throws Exception {
        logger.info("Listing all Jobs");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME_JOB, ENTITY_VERSION);
        ArrayNode items = itemsFuture.get();
        List<Job> jobs = items.findValuesAsText("technicalId").isEmpty() ? List.of() :
                items.findValuesAsText("technicalId").stream().toList(); // dummy to satisfy compilation
        // Convert ArrayNode to List<Job>
        List<Job> jobList = items.findValuesAsText("technicalId").isEmpty() ? List.of() :
                items.findValuesAsText("technicalId").stream().map(id -> {
                    try {
                        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME_JOB, ENTITY_VERSION, UUID.fromString(id));
                        ObjectNode obj = itemFuture.get();
                        return obj.traverse().readValueAs(Job.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).toList();
        return ResponseEntity.ok(jobList);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Job> getJob(@PathVariable @NotBlank String id) throws Exception {
        logger.info("Fetching Job with technicalId: {}", id);
        UUID uuid = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME_JOB, ENTITY_VERSION, uuid);
        ObjectNode obj = itemFuture.get();
        if (obj == null || obj.isEmpty()) {
            logger.error("Job not found with technicalId: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        Job job = obj.traverse().readValueAs(Job.class);
        job.setTechnicalId(uuid);
        return ResponseEntity.ok(job);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CreateResponse> updateJob(@PathVariable @NotBlank String id,
                                                    @RequestBody @Valid JobCreateUpdateDTO jobDto) throws Exception {
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
        processJob(job);
        return ResponseEntity.ok(new CreateResponse(id, "Job updated and processed"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<CreateResponse> deleteJob(@PathVariable @NotBlank String id) throws Exception {
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

    private void processJob(Job job) {
        // TODO: Replace this mock processing with real Cyoda event-driven logic
        logger.info("Processing Job entity with technicalId={} and name={}", job.getTechnicalId(), job.getName());
    }

    // ======== DIGESTREQUEST CRUD (local cache only, skip) ========
    // ======== EMAILDISPATCH CRUD (local cache only, skip) ========

    // ======== DTO classes for validation & mapping ========

    @Data
    public static class JobCreateUpdateDTO {
        @NotBlank
        @Size(max = 255)
        private String name;

        @NotBlank
        @Size(max = 255)
        private String description;

        @NotNull
        private Integer priority;
    }

    private Job toJob(JobCreateUpdateDTO dto) {
        Job job = new Job();
        job.setName(dto.getName());
        job.setDescription(dto.getDescription());
        job.setPriority(dto.getPriority());
        return job;
    }

    @Data
    private static class CreateResponse {
        private final String entityId;
        private final String status;
    }
}