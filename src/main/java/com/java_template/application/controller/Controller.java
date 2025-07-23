package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.CatFactJob;
import com.java_template.application.entity.Interaction;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // --- CatFactJob Endpoints ---

    @PostMapping("/catFactJobs")
    public ResponseEntity<?> createCatFactJob(@RequestBody CatFactJob catFactJob) throws Exception {
        if (catFactJob == null || catFactJob.getScheduledAt() == null) {
            return ResponseEntity.badRequest().body("scheduledAt is required");
        }
        catFactJob.setStatus("PENDING");
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "CatFactJob",
                ENTITY_VERSION,
                catFactJob
        );
        UUID technicalId = idFuture.get();

        // Retrieve the full entity with technicalId
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "CatFactJob",
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode storedNode = itemFuture.get();

        // Map storedNode back to CatFactJob object
        CatFactJob storedJob = JsonUtils.convert(storedNode, CatFactJob.class);
        storedJob.setTechnicalId(technicalId);

        processCatFactJob(storedJob);

        return ResponseEntity.status(201).body(storedJob);
    }

    @GetMapping("/catFactJobs/{id}")
    public ResponseEntity<?> getCatFactJob(@PathVariable String id) throws Exception {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body("CatFactJob not found");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "CatFactJob",
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(404).body("CatFactJob not found");
        }
        CatFactJob job = JsonUtils.convert(node, CatFactJob.class);
        job.setTechnicalId(technicalId);
        return ResponseEntity.ok(job);
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriber) throws Exception {
        if (subscriber == null || subscriber.getEmail() == null || subscriber.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body("Valid email is required");
        }
        subscriber.setSubscribedAt(LocalDateTime.now());
        subscriber.setStatus("ACTIVE");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Subscriber",
                ENTITY_VERSION,
                subscriber
        );
        UUID technicalId = idFuture.get();

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "Subscriber",
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode storedNode = itemFuture.get();

        Subscriber storedSubscriber = JsonUtils.convert(storedNode, Subscriber.class);
        storedSubscriber.setTechnicalId(technicalId);

        processSubscriber(storedSubscriber);

        return ResponseEntity.status(201).body(storedSubscriber);
    }

    @GetMapping("/subscribers/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable String id) throws Exception {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body("Subscriber not found");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "Subscriber",
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(404).body("Subscriber not found");
        }
        Subscriber subscriber = JsonUtils.convert(node, Subscriber.class);
        subscriber.setTechnicalId(technicalId);
        return ResponseEntity.ok(subscriber);
    }

    @GetMapping("/subscribers/count")
    public ResponseEntity<?> getActiveSubscriberCount() throws Exception {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.status", "IEQUALS", "ACTIVE"));
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                "Subscriber",
                ENTITY_VERSION,
                condition,
                true
        );
        ArrayNode activeSubscribers = filteredItemsFuture.get();
        Map<String, Long> response = new HashMap<>();
        response.put("activeSubscribers", (long) activeSubscribers.size());
        return ResponseEntity.ok(response);
    }

    // --- Interaction Endpoints ---

    @PostMapping("/interactions")
    public ResponseEntity<?> createInteraction(@RequestBody Interaction interaction) throws Exception {
        if (interaction == null ||
                interaction.getSubscriberId() == null || interaction.getSubscriberId().isBlank() ||
                interaction.getCatFactJobId() == null || interaction.getCatFactJobId().isBlank() ||
                interaction.getInteractionType() == null || interaction.getInteractionType().isBlank() ||
                interaction.getInteractedAt() == null) {
            return ResponseEntity.badRequest().body("All interaction fields are required");
        }

        // Validate referenced entities exist
        UUID subscriberTechnicalId;
        UUID catFactJobTechnicalId;
        try {
            subscriberTechnicalId = UUID.fromString(interaction.getSubscriberId());
            catFactJobTechnicalId = UUID.fromString(interaction.getCatFactJobId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Referenced Subscriber or CatFactJob does not exist");
        }

        CompletableFuture<ObjectNode> subscriberNodeFuture = entityService.getItem(
                "Subscriber",
                ENTITY_VERSION,
                subscriberTechnicalId
        );
        ObjectNode subscriberNode = subscriberNodeFuture.get();
        if (subscriberNode == null || subscriberNode.isEmpty()) {
            return ResponseEntity.badRequest().body("Referenced Subscriber does not exist");
        }
        CompletableFuture<ObjectNode> jobNodeFuture = entityService.getItem(
                "CatFactJob",
                ENTITY_VERSION,
                catFactJobTechnicalId
        );
        ObjectNode jobNode = jobNodeFuture.get();
        if (jobNode == null || jobNode.isEmpty()) {
            return ResponseEntity.badRequest().body("Referenced CatFactJob does not exist");
        }

        interaction.setStatus("RECORDED");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Interaction",
                ENTITY_VERSION,
                interaction
        );
        UUID technicalId = idFuture.get();

        CompletableFuture<ObjectNode> storedNodeFuture = entityService.getItem(
                "Interaction",
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode storedNode = storedNodeFuture.get();
        Interaction storedInteraction = JsonUtils.convert(storedNode, Interaction.class);
        storedInteraction.setTechnicalId(technicalId);

        processInteraction(storedInteraction);

        return ResponseEntity.status(201).body(storedInteraction);
    }

    @GetMapping("/interactions/{id}")
    public ResponseEntity<?> getInteraction(@PathVariable String id) throws Exception {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body("Interaction not found");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "Interaction",
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(404).body("Interaction not found");
        }
        Interaction interaction = JsonUtils.convert(node, Interaction.class);
        interaction.setTechnicalId(technicalId);
        return ResponseEntity.ok(interaction);
    }

    @GetMapping("/interactions/count")
    public ResponseEntity<?> getInteractionCounts() throws Exception {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                "Interaction",
                ENTITY_VERSION
        );
        ArrayNode allInteractions = itemsFuture.get();

        long emailOpens = 0L;
        long linkClicks = 0L;

        for (JsonNode node : allInteractions) {
            String type = node.get("interactionType") != null ? node.get("interactionType").asText() : null;
            if ("EMAIL_OPEN".equalsIgnoreCase(type)) {
                emailOpens++;
            } else if ("LINK_CLICK".equalsIgnoreCase(type)) {
                linkClicks++;
            }
        }
        Map<String, Long> response = new HashMap<>();
        response.put("emailOpens", emailOpens);
        response.put("linkClicks", linkClicks);
        return ResponseEntity.ok(response);
    }

    // --- Process Methods ---

    private void processCatFactJob(CatFactJob catFactJob) {
        logger.info("Processing CatFactJob with technicalId: {}", catFactJob.getTechnicalId());

        try {
            // Simulate API call - replace with actual HTTP call in real implementation
            String fetchedFact = "Cats sleep 70% of their lives."; // placeholder fact
            catFactJob.setCatFactText(fetchedFact);
            catFactJob.setStatus("PROCESSING");
            logger.info("Fetched cat fact: {}", fetchedFact);

            // Send emails to all active subscribers
            try {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.status", "IEQUALS", "ACTIVE"));
                CompletableFuture<ArrayNode> activeSubsFuture = entityService.getItemsByCondition(
                        "Subscriber",
                        ENTITY_VERSION,
                        condition,
                        true
                );
                ArrayNode activeSubscribers = activeSubsFuture.get();

                for (JsonNode node : activeSubscribers) {
                    String email = node.get("email") != null ? node.get("email").asText() : null;
                    if (email != null) {
                        logger.info("Sending cat fact email to subscriber: {}", email);
                    }
                }
            } catch (Exception e) {
                logger.error("Error sending emails to subscribers: {}", e.getMessage());
            }

            catFactJob.setStatus("COMPLETED");
            logger.info("CatFactJob {} completed successfully", catFactJob.getTechnicalId());
        } catch (Exception e) {
            catFactJob.setStatus("FAILED");
            logger.error("Failed to process CatFactJob {}: {}", catFactJob.getTechnicalId(), e.getMessage());
        }
    }

    private void processSubscriber(Subscriber subscriber) {
        logger.info("Processing Subscriber with technicalId: {}", subscriber.getTechnicalId());

        if (!subscriber.getEmail().contains("@")) {
            logger.error("Invalid email format for subscriber {}", subscriber.getTechnicalId());
            throw new IllegalArgumentException("Invalid email format");
        }

        logger.info("Subscriber {} validated and saved", subscriber.getEmail());
    }

    private void processInteraction(Interaction interaction) {
        logger.info("Processing Interaction with technicalId: {}", interaction.getTechnicalId());

        logger.info("Subscriber {} had interaction {} on CatFactJob {} at {}",
                interaction.getSubscriberId(),
                interaction.getInteractionType(),
                interaction.getCatFactJobId(),
                interaction.getInteractedAt());
    }

    // Utility class for conversions between ObjectNode and entity objects
    private static class JsonUtils {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static <T> T convert(JsonNode node, Class<T> clazz) {
            try {
                return mapper.treeToValue(node, clazz);
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert JSON node to " + clazz.getSimpleName(), e);
            }
        }
    }
}