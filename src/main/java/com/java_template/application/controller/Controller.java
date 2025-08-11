package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.application.entity.job.version_1000.Job;
import com.java_template.application.entity.laureate.version_1000.Laureate;
import com.java_template.application.entity.subscriber.version_1000.Subscriber;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api")
public class Controller {

    private final EntityService entityService;
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST /jobs - Create new Job, triggers JobWorkflowOrchestrator
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody Job jobRequest) {
        try {
            logger.info("📝 Creating new Job: {}", jobRequest.getJobName());

            jobRequest.setStatus("SCHEDULED");
            jobRequest.setCreatedAt(OffsetDateTime.now());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    jobRequest
            );

            UUID technicalId = idFuture.get();

            logger.info("✅ Job created with ID: {} (Workflow orchestrator triggered automatically)", technicalId);

            return ResponseEntity.status(HttpStatus.CREATED).body(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for createJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            // Unwrap the cause from ExecutionException
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.error("Invalid argument for createJob", cause);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Unexpected error in createJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /jobs/{technicalId} - Retrieve Job by technicalId
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(@PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    ENTITY_VERSION,
                    UUID.fromString(technicalId)
            );

            ObjectNode jobNode = itemFuture.get();
            if (jobNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            }
            return ResponseEntity.ok(jobNode);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for getJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            // Unwrap the cause from ExecutionException
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.debug("Job not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            } else {
                logger.error("Unexpected error in getJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /laureates/{technicalId} - Retrieve Laureate by technicalId
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureate(@PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    ENTITY_VERSION,
                    UUID.fromString(technicalId)
            );

            ObjectNode laureateNode = itemFuture.get();
            if (laureateNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Laureate not found");
            }
            return ResponseEntity.ok(laureateNode);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for getLaureate", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            // Unwrap the cause from ExecutionException
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.debug("Laureate not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Laureate not found");
            } else {
                logger.error("Unexpected error in getLaureate", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // POST /subscribers - Create new Subscriber, triggers SubscriberWorkflowOrchestrator
    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriberRequest) {
        try {
            logger.info("📝 Creating new Subscriber: {} ({})",
                       subscriberRequest.getSubscriberId(), subscriberRequest.getContactAddress());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    ENTITY_VERSION,
                    subscriberRequest
            );

            UUID technicalId = idFuture.get();

            logger.info("✅ Subscriber created with ID: {} (Workflow orchestrator triggered automatically)", technicalId);

            return ResponseEntity.status(HttpStatus.CREATED).body(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for createSubscriber", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            // Unwrap the cause from ExecutionException
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.error("Invalid argument for createSubscriber", cause);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Unexpected error in createSubscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /subscribers/{technicalId} - Retrieve Subscriber by technicalId
    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(@PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    ENTITY_VERSION,
                    UUID.fromString(technicalId)
            );

            ObjectNode subscriberNode = itemFuture.get();
            if (subscriberNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }
            return ResponseEntity.ok(subscriberNode);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for getSubscriber", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            // Unwrap the cause from ExecutionException
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.debug("Subscriber not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            } else {
                logger.error("Unexpected error in getSubscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // DTO for technicalId response
    private static class TechnicalIdResponse {
        private final String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }

        public String getTechnicalId() {
            return technicalId;
        }
    }
}