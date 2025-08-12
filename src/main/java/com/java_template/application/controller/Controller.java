package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@Tag(name = "Controller", description = "Event-driven REST API controller for Job, Laureate, and Subscriber entities")
@RestController
@RequestMapping("/api")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST /jobs - Create Job entity and trigger processJob() event indirectly
    @Operation(summary = "Create Job", description = "Create a new Job entity with initial status SCHEDULED and trigger processing")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job creation request", required = true,
            content = @Content(schema = @Schema(implementation = JobCreateRequest.class)))
    @RequestBody JobCreateRequest request) {
        try {
            Job job = new Job();
            job.setJobName(request.getJobName());
            job.setStatus("SCHEDULED");
            job.setCreatedAt(java.time.OffsetDateTime.now().toString());
            job.setDetails(null);

            UUID id = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job).get();

            TechnicalIdResponse response = new TechnicalIdResponse(id.toString());
            return ResponseEntity.ok(response);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Exception in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /jobs/{technicalId} - Retrieve Job entity by technicalId
    @Operation(summary = "Get Job by Technical ID", description = "Retrieve a Job entity by its technical ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job entity found", content = @Content(schema = @Schema(implementation = Job.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(
            @Parameter(name = "technicalId", description = "Technical ID of the Job", required = true)
            @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            ObjectNode node = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    uuid).get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            }
            return ResponseEntity.ok(node);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Exception in getJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /laureates/{technicalId} - Retrieve Laureate entity by technicalId
    @Operation(summary = "Get Laureate by Technical ID", description = "Retrieve a Laureate entity by its technical ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Laureate entity found", content = @Content(schema = @Schema(implementation = Laureate.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Laureate not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the Laureate", required = true)
            @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            ObjectNode node = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    uuid).get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Laureate not found");
            }
            return ResponseEntity.ok(node);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLaureate", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Exception in getLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // POST /subscribers - Create Subscriber entity and trigger processSubscriber() event indirectly
    @Operation(summary = "Create Subscriber", description = "Create a new Subscriber entity and trigger processing")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Subscriber created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber creation request", required = true,
            content = @Content(schema = @Schema(implementation = SubscriberCreateRequest.class)))
    @RequestBody SubscriberCreateRequest request) {
        try {
            Subscriber subscriber = new Subscriber();
            subscriber.setSubscriberName(request.getSubscriberName());
            subscriber.setContactEmail(request.getContactEmail());
            subscriber.setWebhookUrl(request.getWebhookUrl());

            UUID id = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    subscriber).get();

            TechnicalIdResponse response = new TechnicalIdResponse(id.toString());
            return ResponseEntity.ok(response);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createSubscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Exception in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /subscribers/{technicalId} - Retrieve Subscriber entity by technicalId
    @Operation(summary = "Get Subscriber by Technical ID", description = "Retrieve a Subscriber entity by its technical ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Subscriber entity found", content = @Content(schema = @Schema(implementation = Subscriber.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Subscriber not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the Subscriber", required = true)
            @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            ObjectNode node = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    uuid).get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }
            return ResponseEntity.ok(node);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getSubscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Exception in getSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Data
    public static class JobCreateRequest {
        @Schema(description = "Name or identifier of the ingestion job", required = true)
        private String jobName;
    }

    @Data
    public static class SubscriberCreateRequest {
        @Schema(description = "Name or identifier of the subscriber", required = true)
        private String subscriberName;

        @Schema(description = "Email address for notifications", required = true)
        private String contactEmail;

        @Schema(description = "Optional webhook URL for notifications")
        private String webhookUrl;
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity", required = true)
        private final String technicalId;
    }
}