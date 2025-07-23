package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper;

    // --- CatFactJob Endpoints ---

    @PostMapping("/catFactJobs")
    public ResponseEntity<?> createCatFactJob(@RequestBody @Valid CatFactJob catFactJob) throws ExecutionException, InterruptedException, JsonProcessingException {
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
        CatFactJob storedJob = objectMapper.treeToValue(storedNode, CatFactJob.class);
        storedJob.setTechnicalId(technicalId);

        return ResponseEntity.status(201).body(storedJob);
    }

    @GetMapping("/catFactJobs/{id}")
    public ResponseEntity<?> getCatFactJob(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
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
        CatFactJob job = objectMapper.treeToValue(node, CatFactJob.class);
        job.setTechnicalId(technicalId);
        return ResponseEntity.ok(job);
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody @Valid Subscriber subscriber) throws ExecutionException, InterruptedException, JsonProcessingException {
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

        Subscriber storedSubscriber = objectMapper.treeToValue(storedNode, Subscriber.class);
        storedSubscriber.setTechnicalId(technicalId);

        return ResponseEntity.status(201).body(storedSubscriber);
    }

    @GetMapping("/subscribers/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
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
        Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
        subscriber.setTechnicalId(technicalId);
        return ResponseEntity.ok(subscriber);
    }

    @GetMapping("/subscribers/count")
    public ResponseEntity<?> getActiveSubscriberCount() throws ExecutionException, InterruptedException {
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
    public ResponseEntity<?> createInteraction(@RequestBody @Valid Interaction interaction) throws ExecutionException, InterruptedException, JsonProcessingException {
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
        Interaction storedInteraction = objectMapper.treeToValue(storedNode, Interaction.class);
        storedInteraction.setTechnicalId(technicalId);

        return ResponseEntity.status(201).body(storedInteraction);
    }

    @GetMapping("/interactions/{id}")
    public ResponseEntity<?> getInteraction(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
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
        Interaction interaction = objectMapper.treeToValue(node, Interaction.class);
        interaction.setTechnicalId(technicalId);
        return ResponseEntity.ok(interaction);
    }

    @GetMapping("/interactions/count")
    public ResponseEntity<?> getInteractionCounts() throws ExecutionException, InterruptedException {
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
}