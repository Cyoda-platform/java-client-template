package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.CatFactInteraction;
import com.java_template.application.entity.Subscriber;
import com.java_template.application.entity.WeeklyCatFactJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    // POST /controller/weekly-cat-fact-jobs
    @PostMapping("/weekly-cat-fact-jobs")
    public ResponseEntity<Map<String, String>> createWeeklyCatFactJob(@RequestBody Map<String, String> payload) {
        try {
            String subscriberEmail = payload.get("subscriberEmail");
            if (subscriberEmail == null || subscriberEmail.isBlank()) {
                logger.error("Subscriber email is missing or blank");
                return ResponseEntity.badRequest().build();
            }
            WeeklyCatFactJob job = new WeeklyCatFactJob();
            job.setSubscriberEmail(subscriberEmail);
            job.setStatus("PENDING");
            job.setScheduledAt(new Date().toInstant().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(WeeklyCatFactJob.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();

            // processWeeklyCatFactJob has been extracted to workflow prototype

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Internal server error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /controller/weekly-cat-fact-jobs/{id}
    @GetMapping("/weekly-cat-fact-jobs/{id}")
    public ResponseEntity<WeeklyCatFactJob> getWeeklyCatFactJob(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(WeeklyCatFactJob.ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            WeeklyCatFactJob job = convertObjectNodeToWeeklyCatFactJob(node);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("Error retrieving WeeklyCatFactJob: {}", ee.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Internal server error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // POST /controller/subscribers
    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("email");
            if (email == null || email.isBlank()) {
                logger.error("Subscriber email is missing or blank");
                return ResponseEntity.badRequest().build();
            }
            // Check if already subscribed
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.email", "IEQUALS", email));
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(Subscriber.ENTITY_NAME, ENTITY_VERSION, condition, true);
            ArrayNode filteredNodes = filteredItemsFuture.get();
            if (filteredNodes != null && filteredNodes.size() > 0) {
                logger.info("Subscriber with email {} already exists", email);
                Map<String, String> response = new HashMap<>();
                response.put("technicalId", "existing");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            Subscriber subscriber = new Subscriber();
            subscriber.setEmail(email);
            subscriber.setSubscribedAt(new Date().toInstant().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            UUID technicalId = idFuture.get();

            // processSubscriber has been extracted to workflow prototype

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Internal server error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /controller/subscribers/{id}
    @GetMapping("/subscribers/{id}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Subscriber subscriber = convertObjectNodeToSubscriber(node);
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("Error retrieving Subscriber: {}", ee.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Internal server error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // POST /controller/cat-fact-interactions
    @PostMapping("/cat-fact-interactions")
    public ResponseEntity<Map<String, String>> createCatFactInteraction(@RequestBody Map<String, String> payload) {
        try {
            String subscriberEmail = payload.get("subscriberEmail");
            String catFactId = payload.get("catFactId");
            String interactionType = payload.get("interactionType");

            if (subscriberEmail == null || subscriberEmail.isBlank() ||
                    catFactId == null || catFactId.isBlank() ||
                    interactionType == null || interactionType.isBlank()) {
                logger.error("Missing required fields for CatFactInteraction");
                return ResponseEntity.badRequest().build();
            }
            CatFactInteraction interaction = new CatFactInteraction();
            interaction.setSubscriberEmail(subscriberEmail);
            interaction.setCatFactId(catFactId);
            interaction.setInteractionType(interactionType);
            interaction.setInteractionTimestamp(new Date().toInstant().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(CatFactInteraction.ENTITY_NAME, ENTITY_VERSION, interaction);
            UUID technicalId = idFuture.get();

            // processCatFactInteraction has been extracted to workflow prototype

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Internal server error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /controller/cat-fact-interactions/{id}
    @GetMapping("/cat-fact-interactions/{id}")
    public ResponseEntity<CatFactInteraction> getCatFactInteraction(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(CatFactInteraction.ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            CatFactInteraction interaction = convertObjectNodeToCatFactInteraction(node);
            return ResponseEntity.ok(interaction);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("Error retrieving CatFactInteraction: {}", ee.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Internal server error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper methods to convert ObjectNode to entity classes

    private WeeklyCatFactJob convertObjectNodeToWeeklyCatFactJob(ObjectNode node) {
        WeeklyCatFactJob job = new WeeklyCatFactJob();
        if (node.has("subscriberEmail")) job.setSubscriberEmail(node.get("subscriberEmail").asText());
        if (node.has("status")) job.setStatus(node.get("status").asText());
        if (node.has("scheduledAt")) job.setScheduledAt(node.get("scheduledAt").asText());
        if (node.has("catFact")) job.setCatFact(node.get("catFact").asText());
        return job;
    }

    private Subscriber convertObjectNodeToSubscriber(ObjectNode node) {
        Subscriber subscriber = new Subscriber();
        if (node.has("email")) subscriber.setEmail(node.get("email").asText());
        if (node.has("subscribedAt")) subscriber.setSubscribedAt(node.get("subscribedAt").asText());
        return subscriber;
    }

    private CatFactInteraction convertObjectNodeToCatFactInteraction(ObjectNode node) {
        CatFactInteraction interaction = new CatFactInteraction();
        if (node.has("subscriberEmail")) interaction.setSubscriberEmail(node.get("subscriberEmail").asText());
        if (node.has("catFactId")) interaction.setCatFactId(node.get("catFactId").asText());
        if (node.has("interactionType")) interaction.setInteractionType(node.get("interactionType").asText());
        if (node.has("interactionTimestamp")) interaction.setInteractionTimestamp(node.get("interactionTimestamp").asText());
        return interaction;
    }
}