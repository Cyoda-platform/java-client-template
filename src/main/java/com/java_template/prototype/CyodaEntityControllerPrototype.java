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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/prototype/job")
@Validated
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    // ======== JOB CRUD ========

    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody @Valid JobCreateDto jobDto) throws ExecutionException, InterruptedException {
        Job job = toJobEntity(jobDto);
        CompletableFuture<UUID> idFuture = entityService.addItem("Job", ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();
        logger.info("Created Job with technicalId {}", technicalId);
        processJob(job);
        return ResponseEntity.ok(Map.of("id", technicalId.toString(), "status", "Job processed"));
    }

    @GetMapping
    public ResponseEntity<Job> getJob(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Job", ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            logger.info("Job with technicalId {} not found", id);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        Job job = nodeToJob(node);
        return ResponseEntity.ok(job);
    }

    @PutMapping
    public ResponseEntity<Job> updateJob(@RequestBody @Valid JobUpdateDto jobDto) throws ExecutionException, InterruptedException {
        if (jobDto.getId() == null || jobDto.getId().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Job id must be provided");
        }
        Job updatedJob = toJobEntity(jobDto);
        UUID technicalId = UUID.fromString(jobDto.getId());
        CompletableFuture<UUID> updatedItemId = entityService.updateItem("Job", ENTITY_VERSION, technicalId, updatedJob);
        UUID updatedId = updatedItemId.get();
        if (updatedId == null) {
            logger.info("Job with id {} not found for update", jobDto.getId());
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        logger.info("Updated Job with id {}", updatedId);
        processJob(updatedJob);
        return ResponseEntity.ok(updatedJob);
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteJob(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<UUID> deletedItemId = entityService.deleteItem("Job", ENTITY_VERSION, technicalId);
        UUID deletedId = deletedItemId.get();
        if (deletedId == null) {
            logger.info("Job with id {} not found for delete", id);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        logger.info("Deleted Job with id {}", deletedId);
        return ResponseEntity.ok(Map.of("status", "Job deleted"));
    }

    private void processJob(Job job) {
        // TODO: Replace with real Cyoda event processing logic
        logger.info("Processing Job entity (simulated event): {}", job.getTechnicalId());
    }

    private Job nodeToJob(ObjectNode node) {
        Job job = new Job();
        if (node.has("technicalId")) job.setTechnicalId(node.get("technicalId").asText());
        if (node.has("id")) job.setId(node.get("id").asText());
        if (node.has("name")) job.setName(node.get("name").asText());
        if (node.has("active")) job.setActive(node.get("active").asBoolean());
        return job;
    }

    // ======= DTOs =======

    @Data
    public static class JobCreateDto {
        @NotBlank
        private String technicalId;
        @NotBlank
        @Size(max = 255)
        private String name;
        @NotNull
        private Boolean active;
    }

    @Data
    public static class JobUpdateDto extends JobCreateDto {
        @NotBlank
        private String id;
    }

    // ======= Converters from DTO to Entity =======

    private Job toJobEntity(JobCreateDto dto) {
        Job job = new Job();
        job.setTechnicalId(dto.getTechnicalId());
        job.setName(dto.getName());
        job.setActive(dto.getActive());
        return job;
    }

    private Job toJobEntity(JobUpdateDto dto) {
        Job job = toJobEntity((JobCreateDto) dto);
        job.setId(dto.getId());
        return job;
    }
}