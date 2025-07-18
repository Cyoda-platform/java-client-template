package com.java_template.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Job;
import com.java_template.application.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/jobs")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<String> createJob(@Valid @RequestBody Job job) throws ExecutionException, InterruptedException, JsonProcessingException {
        // Map DTO fields to entity fields if DTO is different, here assuming Job is used directly
        UUID savedJobId = entityService.addItem(job.getTechnicalId(), "1", job).get();
        logger.info("Job created with ID: {}", savedJobId);
        return ResponseEntity.ok(savedJobId.toString());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Job> getJobById(@PathVariable String id) throws JsonProcessingException {
        UUID uuid = UUID.fromString(id);
        ObjectNode node = entityService.getItem(uuid, "1");
        Job job = objectMapper.treeToValue(node, Job.class);
        return ResponseEntity.ok(job);
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateJob(@PathVariable String id, @Valid @RequestBody Job job) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(id);
        job.setTechnicalId(uuid); // Ensure the entity has the correct UUID set
        UUID updatedJobId = entityService.updateItem(uuid, "1", job).get();
        logger.info("Job updated with ID: {}", updatedJobId);
        return ResponseEntity.ok(updatedJobId.toString());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID uuid = UUID.fromString(id);
        UUID deletedId = entityService.deleteItem(uuid, "1").get();
        logger.info("Job deleted with ID: {}", deletedId);
        return ResponseEntity.ok(deletedId.toString());
    }
}