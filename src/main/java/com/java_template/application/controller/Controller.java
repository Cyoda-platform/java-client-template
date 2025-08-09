package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.application.entity.job.version_1000.Job;
import com.java_template.application.entity.laureate.version_1000.Laureate;
import com.java_template.application.entity.subscriber.version_1000.Subscriber;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
                    ENTITY_VERSION,
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

    // POST /laureates - Create new Laureate, triggers LaureateWorkflowOrchestrator
    @PostMapping("/laureates")
    public ResponseEntity<?> createLaureate(@RequestBody Laureate laureateRequest) {
        try {
            logger.info("📝 Creating new Laureate: {} {}",
                       laureateRequest.getFirstname(), laureateRequest.getSurname());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    ENTITY_VERSION,
                    laureateRequest
            );

            UUID technicalId = idFuture.get();

            logger.info("✅ Laureate created with ID: {} (Workflow orchestrator triggered automatically)", technicalId);

            return ResponseEntity.status(HttpStatus.CREATED).body(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for createLaureate", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            // Unwrap the cause from ExecutionException
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.error("Invalid argument for createLaureate", cause);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Unexpected error in createLaureate", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /jobs - Get all Jobs
    @GetMapping("/jobs")
    public ResponseEntity<?> getAllJobs() {
        try {
            logger.info("📋 Retrieving all Jobs");

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Job.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode jobs = itemsFuture.get();

            logger.info("✅ Retrieved {} Jobs", jobs.size());
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            logger.error("Unexpected error in getAllJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /subscribers - Get all Subscribers
    @GetMapping("/subscribers")
    public ResponseEntity<?> getAllSubscribers() {
        try {
            logger.info("📋 Retrieving all Subscribers");

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Subscriber.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode subscribers = itemsFuture.get();

            logger.info("✅ Retrieved {} Subscribers", subscribers.size());
            return ResponseEntity.ok(subscribers);
        } catch (Exception e) {
            logger.error("Unexpected error in getAllSubscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /laureates - Get all Laureates
    @GetMapping("/laureates")
    public ResponseEntity<?> getAllLaureates() {
        try {
            logger.info("📋 Retrieving all Laureates");

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Laureate.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode laureates = itemsFuture.get();

            logger.info("✅ Retrieved {} Laureates", laureates.size());
            return ResponseEntity.ok(laureates);
        } catch (Exception e) {
            logger.error("Unexpected error in getAllLaureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // PUT /jobs/{technicalId} - Update Job, triggers workflow orchestrator
    @PutMapping("/jobs/{technicalId}")
    public ResponseEntity<?> updateJob(@PathVariable String technicalId, @RequestBody Job jobRequest) {
        try {
            logger.info("🔄 Updating Job: {} (Status: {})", technicalId, jobRequest.getStatus());

            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> updateFuture = entityService.updateItem(
                    Job.ENTITY_NAME,
                    ENTITY_VERSION,
                    id,
                    jobRequest
            );

            UUID updatedId = updateFuture.get();

            logger.info("✅ Job updated: {} (Workflow orchestrator triggered automatically)", updatedId);
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for updateJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            // Unwrap the cause from ExecutionException
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.error("Invalid argument for updateJob", cause);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                logger.debug("Job not found for update: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            } else {
                logger.error("Unexpected error in updateJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in updateJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // PUT /subscribers/{technicalId} - Update Subscriber, triggers workflow orchestrator
    @PutMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> updateSubscriber(@PathVariable String technicalId, @RequestBody Subscriber subscriberRequest) {
        try {
            logger.info("🔄 Updating Subscriber: {} (Active: {})", technicalId, subscriberRequest.getActive());

            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> updateFuture = entityService.updateItem(
                    Subscriber.ENTITY_NAME,
                    ENTITY_VERSION,
                    id,
                    subscriberRequest
            );

            UUID updatedId = updateFuture.get();

            logger.info("✅ Subscriber updated: {} (Workflow orchestrator triggered automatically)", updatedId);
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for updateSubscriber", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            // Unwrap the cause from ExecutionException
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.error("Invalid argument for updateSubscriber", cause);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                logger.debug("Subscriber not found for update: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            } else {
                logger.error("Unexpected error in updateSubscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in updateSubscriber", e);
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