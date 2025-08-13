package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
@Tag(name = "Controller", description = "Event-Driven REST API Controller for Job, Laureate, and Subscriber entities")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // Job Endpoints

    @Operation(summary = "Create a new Job", description = "Creates a new Job entity (immutable creation, triggers processJob)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Job created successfully", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody JobCreateRequest request) {
        try {
            Job job = new Job();
            job.setJobName(request.getJobName());
            job.setTriggerTime(request.getTriggerTime());
            job.setStatus("SCHEDULED");
            CompletableFuture<UUID> idFuture = entityService.addItem(Job.ENTITY_NAME, String.valueOf(Job.ENTITY_VERSION), job);
            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
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
        } catch (Exception e) {
            logger.error("Exception in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "Retrieve Job by technicalId", description = "Retrieves Job entity by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Job retrieved successfully", content = @Content(schema = @Schema(implementation = Job.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJobById(@Parameter(name = "technicalId", description = "Technical ID of the Job") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Job.ENTITY_NAME, String.valueOf(Job.ENTITY_VERSION), id);
            ObjectNode jobNode = itemFuture.get();
            if (jobNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            }
            return ResponseEntity.ok(jobNode);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getJobById", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getJobById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Exception in getJobById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // Laureate Endpoints

    @Operation(summary = "Retrieve Laureate by technicalId", description = "Retrieve stored laureate data by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Laureate retrieved successfully", content = @Content(schema = @Schema(implementation = Laureate.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Laureate not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureateById(@Parameter(name = "technicalId", description = "Technical ID of the Laureate") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Laureate.ENTITY_NAME, String.valueOf(Laureate.ENTITY_VERSION), id);
            ObjectNode laureateNode = itemFuture.get();
            if (laureateNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Laureate not found");
            }
            return ResponseEntity.ok(laureateNode);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getLaureateById", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLaureateById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Exception in getLaureateById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // Subscriber Endpoints

    @Operation(summary = "Create a new Subscriber", description = "Creates a new Subscriber entity (immutable creation, triggers processSubscriber)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscriber created successfully", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody SubscriberCreateRequest request) {
        try {
            Subscriber subscriber = new Subscriber();
            subscriber.setContactEmail(request.getContactEmail());
            subscriber.setActive(request.getActive());
            // subscriberId is assumed to be generated by the system and not client-provided
            CompletableFuture<UUID> idFuture = entityService.addItem(Subscriber.ENTITY_NAME, String.valueOf(Subscriber.ENTITY_VERSION), subscriber);
            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
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
        } catch (Exception e) {
            logger.error("Exception in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "Retrieve Subscriber by technicalId", description = "Retrieves Subscriber entity by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscriber retrieved successfully", content = @Content(schema = @Schema(implementation = Subscriber.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Subscriber not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriberById(@Parameter(name = "technicalId", description = "Technical ID of the Subscriber") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Subscriber.ENTITY_NAME, String.valueOf(Subscriber.ENTITY_VERSION), id);
            ObjectNode subscriberNode = itemFuture.get();
            if (subscriberNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }
            return ResponseEntity.ok(subscriberNode);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getSubscriberById", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getSubscriberById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Exception in getSubscriberById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // Static DTO classes for requests and responses

    @Data
    @Schema(name = "JobCreateRequest", description = "Request payload to create a Job")
    public static class JobCreateRequest {
        @Schema(description = "Name/identifier of the ingestion job", example = "Ingest Nobel Laureates Data", required = true)
        private String jobName;

        @Schema(description = "Timestamp when the job was scheduled", example = "2024-07-01T09:00:00Z", required = true)
        private String triggerTime;
    }

    @Data
    @Schema(name = "SubscriberCreateRequest", description = "Request payload to create a Subscriber")
    public static class SubscriberCreateRequest {
        @Schema(description = "Subscriber email address for notifications", example = "subscriber@example.com", required = true)
        private String contactEmail;

        @Schema(description = "Indicates if subscriber is active and should receive notifications", example = "true", required = true)
        private Boolean active;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technicalId of created entity")
    public static class TechnicalIdResponse {
        private final String technicalId;
    }
}