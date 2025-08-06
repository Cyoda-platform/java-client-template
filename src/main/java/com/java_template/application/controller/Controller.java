package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/api")
@Slf4j
@AllArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // --- JOB ENDPOINTS ---

    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody Job job) {
        try {
            if (!job.isValid()) {
                logger.error("Invalid Job data");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Job data"));
            }
            job.setCreatedAt(java.time.Instant.now().toString());
            job.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();

            logger.info("Job created with technicalId={}", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Job not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Job job = node.traverse().readValueAs(Job.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for job technicalId={}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                logger.error("Job not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("Error fetching job", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        } catch (Exception e) {
            logger.error("Error fetching job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

    // --- PET ENDPOINTS ---

    @GetMapping("/pets/{technicalId}")
    public ResponseEntity<?> getPet(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Pet.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Pet not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Pet pet = node.traverse().readValueAs(Pet.class);
            return ResponseEntity.ok(pet);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for pet technicalId={}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                logger.error("Pet not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("Error fetching pet", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        } catch (Exception e) {
            logger.error("Error fetching pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

    @GetMapping("/pets")
    public ResponseEntity<?> getPets(@RequestParam(required = false) String status) {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Pet.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode arrayNode = itemsFuture.get();
            List<Pet> pets = new ArrayList<>();
            if (arrayNode != null) {
                for (var jsonNode : arrayNode) {
                    Pet pet = jsonNode.traverse().readValueAs(Pet.class);
                    if (status == null || status.isBlank() || status.equalsIgnoreCase(pet.getStatus())) {
                        pets.add(pet);
                    }
                }
            }
            return ResponseEntity.ok(pets);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getPets", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error fetching pets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

    // --- SUBSCRIBER ENDPOINTS ---

    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            if (!subscriber.isValid()) {
                logger.error("Invalid Subscriber data");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Subscriber data"));
            }
            subscriber.setSubscribedAt(java.time.Instant.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, subscriber);
            UUID technicalId = idFuture.get();

            logger.info("Subscriber created with technicalId={}", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Subscriber not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Subscriber subscriber = node.traverse().readValueAs(Subscriber.class);
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for subscriber technicalId={}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                logger.error("Subscriber not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            logger.error("Error fetching subscriber", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        } catch (Exception e) {
            logger.error("Error fetching subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

    @GetMapping("/subscribers")
    public ResponseEntity<?> getSubscribers(@RequestParam(required = false) String petType) {
        try {
            if (petType == null || petType.isBlank()) {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Subscriber.ENTITY_NAME, ENTITY_VERSION);
                ArrayNode arrayNode = itemsFuture.get();
                List<Subscriber> subscribers = new ArrayList<>();
                if (arrayNode != null) {
                    for (var jsonNode : arrayNode) {
                        Subscriber subscriber = jsonNode.traverse().readValueAs(Subscriber.class);
                        subscribers.add(subscriber);
                    }
                }
                return ResponseEntity.ok(subscribers);
            } else {
                Condition cond = Condition.of("$.preferredPetTypes", "ICONTAINS", petType);
                SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", cond);
                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(Subscriber.ENTITY_NAME, ENTITY_VERSION, conditionRequest, true);
                ArrayNode arrayNode = filteredItemsFuture.get();
                List<Subscriber> subscribers = new ArrayList<>();
                if (arrayNode != null) {
                    for (var jsonNode : arrayNode) {
                        Subscriber subscriber = jsonNode.traverse().readValueAs(Subscriber.class);
                        subscribers.add(subscriber);
                    }
                }
                return ResponseEntity.ok(subscribers);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getSubscribers", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error fetching subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

}