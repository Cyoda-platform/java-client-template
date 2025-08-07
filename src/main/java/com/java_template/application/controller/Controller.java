package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // ------------------- JOB ENDPOINTS -------------------

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, UUID>> createJob(@RequestBody Map<String, String> request) throws ExecutionException, InterruptedException {
        String externalId = request.get("externalId");
        if (externalId == null || externalId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Job job = new Job();
        job.setExternalId(externalId);
        job.setState("SCHEDULED");

        CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();
        job.setId(technicalId);

        Map<String, UUID> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Job> getJob(@PathVariable("id") String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Job job = objectMapper.treeToValue(node, Job.class);
        job.setId(technicalId);
        return ResponseEntity.ok(job);
    }

    // ------------------- LAUREATE ENDPOINTS -------------------

    @GetMapping("/laureates/{id}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable("id") String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
        laureate.setId(technicalId);
        return ResponseEntity.ok(laureate);
    }

    // ------------------- SUBSCRIBER ENDPOINTS -------------------

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, UUID>> createSubscriber(@Valid @RequestBody Subscriber subscriber) throws ExecutionException, InterruptedException {
        if (subscriber.getContactEmail() == null || subscriber.getContactEmail().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (subscriber.getActive() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
        UUID technicalId = idFuture.get();
        subscriber.setId(technicalId);

        Map<String, UUID> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/subscribers/{id}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable("id") String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
        subscriber.setId(technicalId);
        return ResponseEntity.ok(subscriber);
    }
}