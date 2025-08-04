package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.CatFact;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/controller")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private final AtomicLong weeklyCatFactJobIdCounter = new AtomicLong(1);
    private final AtomicLong catFactIdCounter = new AtomicLong(1);
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    // POST /controller/weeklyCatFactJob - create WeeklyCatFactJob and trigger processing
    @PostMapping("/weeklyCatFactJob")
    public ResponseEntity<Map<String, String>> createWeeklyCatFactJob() {
        try {
            WeeklyCatFactJob job = new WeeklyCatFactJob();
            job.setStatus("PENDING");
            job.setCatFact("");
            job.setSubscriberCount(0);
            job.setEmailSentDate(null);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    WeeklyCatFactJob.ENTITY_NAME,
                    ENTITY_VERSION,
                    job
            );
            UUID technicalId = idFuture.get();

            logger.info("Created WeeklyCatFactJob with id {}", technicalId);

            // processWeeklyCatFactJob removed

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument creating WeeklyCatFactJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error creating WeeklyCatFactJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /controller/weeklyCatFactJob/{id} - retrieve WeeklyCatFactJob by id
    @GetMapping("/weeklyCatFactJob/{id}")
    public ResponseEntity<WeeklyCatFactJob> getWeeklyCatFactJob(@PathVariable String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    WeeklyCatFactJob.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            WeeklyCatFactJob job = objectMapper.treeToValue(node, WeeklyCatFactJob.class);

            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format WeeklyCatFactJob id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("Execution exception getting WeeklyCatFactJob id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Error getting WeeklyCatFactJob id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // POST /controller/subscriber - create Subscriber and trigger processing
    @PostMapping("/subscriber")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            if (email == null || email.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Check if email already subscribed using getItemsByCondition
            Condition condition = Condition.of("$.email", "IEQUALS", email);
            SearchConditionRequest searchRequest = SearchConditionRequest.group("AND", condition);
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    ENTITY_VERSION,
                    searchRequest,
                    true
            );
            ArrayNode matchedSubscribers = filteredItemsFuture.get();

            boolean exists = false;
            if (matchedSubscribers != null && matchedSubscribers.size() > 0) {
                for (int i = 0; i < matchedSubscribers.size(); i++) {
                    ObjectNode node = (ObjectNode) matchedSubscribers.get(i);
                    if (node.has("status") && "ACTIVE".equalsIgnoreCase(node.get("status").asText())) {
                        exists = true;
                        break;
                    }
                }
            }

            if (exists) {
                logger.info("Subscriber with email {} already exists", email);
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            Subscriber subscriber = new Subscriber();
            subscriber.setEmail(email);
            subscriber.setSubscribedDate(LocalDateTime.now());
            subscriber.setStatus("ACTIVE");
            subscriber.setInteractionCount(0);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    ENTITY_VERSION,
                    subscriber
            );
            UUID technicalId = idFuture.get();

            logger.info("Created Subscriber with id {} and email {}", technicalId, email);

            // processSubscriber removed

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument creating Subscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error creating Subscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /controller/subscriber/{id} - retrieve Subscriber by id
    @GetMapping("/subscriber/{id}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);

            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format Subscriber id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("Execution exception getting Subscriber id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Error getting Subscriber id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /controller/catFact/{id} - retrieve CatFact by id
    @GetMapping("/catFact/{id}")
    public ResponseEntity<CatFact> getCatFact(@PathVariable String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    CatFact.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            CatFact catFact = objectMapper.treeToValue(node, CatFact.class);

            return ResponseEntity.ok(catFact);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format CatFact id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("Execution exception getting CatFact id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Error getting CatFact id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

} // end Controller