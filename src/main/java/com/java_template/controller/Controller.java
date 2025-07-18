package com.java_template.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Job;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Validated
@RestController
@RequestMapping(path = "/prototype/job")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody @Valid JobDTO jobDTO) {
        Job job = fromJobDTO(jobDTO);
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem("Job", ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();
            job.setTechnicalId(technicalId);
            logger.info("Job created with technicalId {}", technicalId);
            return ResponseEntity.ok(Map.of("id", technicalId.toString(), "status", "processed"));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error creating Job: {}", e.getMessage(), e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error creating Job");
        }
    }

    @GetMapping
    public ResponseEntity<Job> getJob(@RequestParam @NotBlank String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (Exception e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid UUID format for id");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Job", ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.join();
        if (node == null || node.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Job with id " + id + " not found");
        }
        Job job = mapObjectNodeToJob(node);
        logger.info("Job retrieved with technicalId {}", id);
        return ResponseEntity.ok(job);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateJob(@RequestBody @Valid JobUpdateDTO jobUpdateDTO) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(jobUpdateDTO.getId());
        } catch (Exception e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid UUID format for id");
        }
        CompletableFuture<ObjectNode> existingItemFuture = entityService.getItem("Job", ENTITY_VERSION, technicalId);
        ObjectNode existingNode = existingItemFuture.join();
        if (existingNode == null || existingNode.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Job with id " + jobUpdateDTO.getId() + " not found");
        }
        Job existingJob = mapObjectNodeToJob(existingNode);
        Job updatedJob = fromJobUpdateDTO(jobUpdateDTO, existingJob);
        try {
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem("Job", ENTITY_VERSION, technicalId, updatedJob);
            UUID updatedTechnicalId = updatedIdFuture.get();
            logger.info("Job updated with technicalId {}", updatedTechnicalId);
            return ResponseEntity.ok(Map.of("id", updatedTechnicalId.toString(), "status", "processed"));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error updating Job: {}", e.getMessage(), e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error updating Job");
        }
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteJob(@RequestParam @NotBlank String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (Exception e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid UUID format for id");
        }
        try {
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem("Job", ENTITY_VERSION, technicalId);
            UUID deletedTechnicalId = deletedIdFuture.get();
            logger.info("Job deleted with technicalId {}", deletedTechnicalId);
            return ResponseEntity.ok(Map.of("id", deletedTechnicalId.toString(), "status", "deleted"));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error deleting Job: {}", e.getMessage(), e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Job with id " + id + " not found");
        }
    }

    @Data
    public static class JobDTO {
        @NotBlank
        private String name;

        @NotBlank
        private String schedule;

        @NotBlank
        private String description;
    }

    @Data
    public static class JobUpdateDTO {
        @NotBlank
        private String id;

        @NotBlank
        private String name;

        @NotBlank
        private String schedule;

        @NotBlank
        private String description;
    }

    private Job fromJobDTO(JobDTO dto) {
        Job job = new Job();
        job.setName(dto.getName());
        job.setSchedule(dto.getSchedule());
        job.setDescription(dto.getDescription());
        return job;
    }

    private Job fromJobUpdateDTO(JobUpdateDTO dto, Job existing) {
        existing.setName(dto.getName());
        existing.setSchedule(dto.getSchedule());
        existing.setDescription(dto.getDescription());
        return existing;
    }

    private Job mapObjectNodeToJob(ObjectNode node) {
        Job job = new Job();
        if (node.has("technicalId") && !node.get("technicalId").isNull()) {
            try {
                job.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid technicalId UUID format in stored data");
            }
        }
        if (node.has("name")) {
            job.setName(node.get("name").asText());
        }
        if (node.has("schedule")) {
            job.setSchedule(node.get("schedule").asText());
        }
        if (node.has("description")) {
            job.setDescription(node.get("description").asText());
        }
        job.setId(job.getTechnicalId() != null ? job.getTechnicalId().toString() : null);
        return job;
    }
}