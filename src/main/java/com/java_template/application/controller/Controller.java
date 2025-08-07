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
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/api")
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // ----------------- JOB ENDPOINTS -----------------

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@Valid @RequestBody Job jobRequest) throws ExecutionException, InterruptedException {
        if (jobRequest.getJobName() == null || jobRequest.getJobName().isBlank()
                || jobRequest.getScheduledTime() == null || jobRequest.getScheduledTime().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Job job = new Job();
        job.setJobName(jobRequest.getJobName());
        job.setScheduledTime(jobRequest.getScheduledTime());
        job.setStatus("SCHEDULED");
        job.setCreatedAt(java.time.OffsetDateTime.now().toString());
        job.setResultSummary(null);

        CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId.toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<Job> getJob(@PathVariable String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, uuid);
        ObjectNode node = itemFuture.get();
        if (node == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Job job = objectMapper.treeToValue(node, Job.class);
        return ResponseEntity.ok(job);
    }

    // ----------------- LAUREATE ENDPOINTS -----------------

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, uuid);
        ObjectNode node = itemFuture.get();
        if (node == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
        return ResponseEntity.ok(laureate);
    }

    // ----------------- SUBSCRIBER ENDPOINTS -----------------

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@Valid @RequestBody Subscriber subscriberRequest) throws ExecutionException, InterruptedException {
        if (subscriberRequest.getContactType() == null || subscriberRequest.getContactType().isBlank()
                || subscriberRequest.getContactValue() == null || subscriberRequest.getContactValue().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Subscriber subscriber = new Subscriber();
        subscriber.setContactType(subscriberRequest.getContactType());
        subscriber.setContactValue(subscriberRequest.getContactValue());
        subscriber.setActive(subscriberRequest.getActive() != null ? subscriberRequest.getActive() : true);

        CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
        UUID technicalId = idFuture.get();

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId.toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, uuid);
        ObjectNode node = itemFuture.get();
        if (node == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
        return ResponseEntity.ok(subscriber);
    }

}