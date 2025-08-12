package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST /jobs → Create Job entity (status=SCHEDULED) and return technicalId
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody Job job) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createJob", e);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Exception in createJob", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    // GET /jobs/{technicalId} → Retrieve Job entity by technicalId
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(@PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode jobNode = itemFuture.get();
            if (jobNode == null || jobNode.isEmpty()) {
                return ResponseEntity.status(404).body("Job not found");
            }
            return ResponseEntity.ok(jobNode);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getJob", e);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Exception in getJob", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    // GET /laureates/{technicalId} → Retrieve Laureate entity by technicalId
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureate(@PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode laureateNode = itemFuture.get();
            if (laureateNode == null || laureateNode.isEmpty()) {
                return ResponseEntity.status(404).body("Laureate not found");
            }
            return ResponseEntity.ok(laureateNode);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLaureate", e);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Exception in getLaureate", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    // POST /subscribers → Create Subscriber entity and return technicalId
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
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createSubscriber", e);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Exception in createSubscriber", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    // GET /subscribers/{technicalId} → Retrieve Subscriber entity by technicalId
    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(@PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode subscriberNode = itemFuture.get();
            if (subscriberNode == null || subscriberNode.isEmpty()) {
                return ResponseEntity.status(404).body("Subscriber not found");
            }
            return ResponseEntity.ok(subscriberNode);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getSubscriber", e);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Exception in getSubscriber", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    @Data
    public static class TechnicalIdResponse {
        private final String technicalId;
    }
}