package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;

import com.java_template.common.dto.SearchConditionRequest;
import com.java_template.common.dto.Condition;

@RestController
@RequestMapping("/api")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST /jobs - create Job entity with status SCHEDULED
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody Job job) {
        try {
            // status SCHEDULED must be set by client or we set it here forcibly
            if (job.getStatus() == null || job.getStatus().isBlank()) {
                job.setStatus("SCHEDULED");
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                Job.ENTITY_NAME,
                String.valueOf(Job.ENTITY_VERSION),
                job
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Exception in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /jobs/{technicalId} - retrieve Job by technicalId
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJobById(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Job.ENTITY_NAME,
                String.valueOf(Job.ENTITY_VERSION),
                uuid
            );
            ObjectNode jobNode = itemFuture.get();
            if (jobNode == null || jobNode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            }
            return ResponseEntity.ok(jobNode);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getJobById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Exception in getJobById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /laureates/{technicalId} - retrieve Laureate by technicalId
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureateById(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION),
                uuid
            );
            ObjectNode laureateNode = itemFuture.get();
            if (laureateNode == null || laureateNode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Laureate not found");
            }
            return ResponseEntity.ok(laureateNode);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLaureateById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Exception in getLaureateById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /laureates?category=Chemistry (optional filtering by category)
    @GetMapping("/laureates")
    public ResponseEntity<?> getLaureatesByCategory(@RequestParam(required = false) String category) {
        try {
            CompletableFuture<ArrayNode> itemsFuture;
            if (category == null || category.isBlank()) {
                itemsFuture = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION)
                );
            } else {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.category", "EQUALS", category)
                );
                itemsFuture = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    condition,
                    true
                );
            }
            ArrayNode laureates = itemsFuture.get();
            return ResponseEntity.ok(laureates);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLaureatesByCategory", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Exception in getLaureatesByCategory", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // POST /subscribers - create Subscriber entity
    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                subscriber
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createSubscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Exception in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /subscribers/{technicalId} - retrieve Subscriber by technicalId
    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriberById(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                uuid
            );
            ObjectNode subscriberNode = itemFuture.get();
            if (subscriberNode == null || subscriberNode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }
            return ResponseEntity.ok(subscriberNode);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getSubscriberById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Exception in getSubscriberById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Data
    public static class TechnicalIdResponse {
        private final String technicalId;
    }
}