package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/api")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper;

    // POST /api/jobs - create Job, trigger ingestion
    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Job job) throws ExecutionException, InterruptedException {
        try {
            if (job == null || !job.isValid()) {
                logger.error("Invalid Job data received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            job.setStatus("PENDING");

            // Add job via EntityService
            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalUUID = idFuture.get();
            String technicalId = "job-" + technicalUUID.toString();

            logger.info("Job created with technicalId {}", technicalId);

            // processJob method removed during extraction

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in createJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /api/jobs/{id} - retrieve Job by technicalId
    @GetMapping("/jobs/{id}")
    public ResponseEntity<Job> getJob(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            UUID technicalUUID;
            try {
                technicalUUID = UUID.fromString(id.replaceFirst("job-", ""));
            } catch (Exception e) {
                logger.error("Invalid UUID format for job id: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Job not found with technicalId {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Job job = objectMapper.treeToValue(node, Job.class);
            return ResponseEntity.ok(job);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (JsonProcessingException e) {
            logger.error("JSON processing exception in getJob: {}", e.getMessage());
            throw e; // propagate as declared
        } catch (Exception e) {
            logger.error("Exception in getJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // POST /api/subscribers - create Subscriber
    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriber) throws ExecutionException, InterruptedException {
        try {
            if (subscriber == null || !subscriber.isValid()) {
                logger.error("Invalid Subscriber data received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            UUID technicalUUID = idFuture.get();
            String technicalId = "sub-" + technicalUUID.toString();

            logger.info("Subscriber created with technicalId {}", technicalId);

            // processSubscriber method removed during extraction

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in createSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /api/subscribers/{id} - retrieve Subscriber
    @GetMapping("/subscribers/{id}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            UUID technicalUUID;
            try {
                technicalUUID = UUID.fromString(id.replaceFirst("sub-", ""));
            } catch (Exception e) {
                logger.error("Invalid UUID format for subscriber id: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Subscriber not found with technicalId {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
            return ResponseEntity.ok(subscriber);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (JsonProcessingException e) {
            logger.error("JSON processing exception in getSubscriber: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Exception in getSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /api/laureates/{id} - retrieve Laureate (no POST endpoint)
    @GetMapping("/laureates/{id}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            UUID technicalUUID;
            try {
                technicalUUID = UUID.fromString(id.replaceFirst("laureate-", ""));
            } catch (Exception e) {
                logger.error("Invalid UUID format for laureate id: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Laureate not found with technicalId {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
            return ResponseEntity.ok(laureate);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getLaureate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (JsonProcessingException e) {
            logger.error("JSON processing exception in getLaureate: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Exception in getLaureate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // notifySubscribers method
    private void notifySubscribers() {
        logger.info("Notifying active subscribers...");
        try {
            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(Subscriber.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode subscribers = subscribersFuture.get();
            if (subscribers == null) return;

            for (int i = 0; i < subscribers.size(); i++) {
                ObjectNode subscriberNode = (ObjectNode) subscribers.get(i);
                Subscriber subscriber = objectMapper.treeToValue(subscriberNode, Subscriber.class);
                Boolean active = subscriber.getActive();
                if (Boolean.TRUE.equals(active)) {
                    String technicalId = subscriberNode.has("technicalId") ? subscriberNode.get("technicalId").asText() : "unknown";
                    logger.info("Notifying subscriber {} via {}: {}", technicalId, subscriber.getContactType(), subscriber.getContactValue());
                    // Real implementation could send email or webhook here
                }
            }
        } catch (Exception e) {
            logger.error("Error notifying subscribers: {}", e.getMessage());
        }
    }
}