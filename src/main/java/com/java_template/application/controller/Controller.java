package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;

@RestController
@RequestMapping("/api")
@Tag(name = "Controller", description = "Event-driven REST API Controller for Jobs, Laureates, and Subscribers")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // Job endpoints

    @Operation(summary = "Create a new Job", description = "Create a Job entity and trigger the ingestion workflow")
    @ApiResponse(responseCode = "200", description = "Job created successfully",
            content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody JobCreateRequest request) {
        try {
            Job job = new Job();
            job.setJobName(request.getJobName());
            job.setSourceUrl(request.getSourceUrl());
            // status, createdAt, finishedAt will be managed by workflow

            if (!job.isValid()) {
                return ResponseEntity.badRequest().body("Invalid Job data");
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job);

            UUID technicalId = idFuture.get();

            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Internal server error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal server error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "Get a Job by technicalId", description = "Retrieve a Job entity by its technicalId")
    @ApiResponse(responseCode = "200", description = "Job found",
            content = @Content(schema = @Schema(implementation = Job.class)))
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(
            @Parameter(name = "technicalId", description = "Technical ID of the Job")
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    id);
            ObjectNode jobNode = itemFuture.get();
            if (jobNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            }
            return ResponseEntity.ok(jobNode);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Internal server error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal server error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // Laureate endpoints

    @Operation(summary = "Get a Laureate by technicalId", description = "Retrieve a Laureate entity by its technicalId")
    @ApiResponse(responseCode = "200", description = "Laureate found",
            content = @Content(schema = @Schema(implementation = Laureate.class)))
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the Laureate")
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    id);
            ObjectNode laureateNode = itemFuture.get();
            if (laureateNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Laureate not found");
            }
            return ResponseEntity.ok(laureateNode);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Internal server error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal server error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // Subscriber endpoints

    @Operation(summary = "Create a new Subscriber", description = "Create a Subscriber entity")
    @ApiResponse(responseCode = "200", description = "Subscriber created successfully",
            content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody SubscriberCreateRequest request) {
        try {
            Subscriber subscriber = new Subscriber();
            subscriber.setContactType(request.getContactType());
            subscriber.setContactAddress(request.getContactAddress());
            subscriber.setActive(request.getActive());
            // subscribedAt managed by workflow or persistence layer

            if (!subscriber.isValid()) {
                return ResponseEntity.badRequest().body("Invalid Subscriber data");
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    subscriber);

            UUID technicalId = idFuture.get();

            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Internal server error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal server error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "Get a Subscriber by technicalId", description = "Retrieve a Subscriber entity by its technicalId")
    @ApiResponse(responseCode = "200", description = "Subscriber found",
            content = @Content(schema = @Schema(implementation = Subscriber.class)))
    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the Subscriber")
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    id);
            ObjectNode subscriberNode = itemFuture.get();
            if (subscriberNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }
            return ResponseEntity.ok(subscriberNode);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Internal server error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal server error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // DTOs for request and response

    @Data
    public static class JobCreateRequest {
        @Schema(description = "Name/identifier of the ingestion job", example = "Nobel Laureates Ingestion April 2024", required = true)
        private String jobName;

        @Schema(description = "URL of the OpenDataSoft API endpoint", example = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records", required = true)
        private String sourceUrl;
    }

    @Data
    public static class SubscriberCreateRequest {
        @Schema(description = "Type of contact: email, webhook, etc.", example = "email", required = true)
        private String contactType;

        @Schema(description = "Email address or webhook URL", example = "user@example.com", required = true)
        private String contactAddress;

        @Schema(description = "Subscriber active status", example = "true", required = true)
        private Boolean active;
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity", example = "123e4567-e89b-12d3-a456-426614174000")
        private final String technicalId;
    }
}