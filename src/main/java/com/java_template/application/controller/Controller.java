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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Nobel Laureates Data Ingestion API", description = "Controller for Jobs, Laureates, and Subscribers")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // Job endpoints

    @PostMapping("/jobs")
    @Operation(summary = "Create a new ingestion job", description = "Create a new ingestion job that triggers the ingestion workflow")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Job created successfully",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public CompletableFuture<TechnicalIdResponse> createJob(@RequestBody JobRequest request) {
        try {
            Job job = new Job();
            job.setApiUrl(request.getApiUrl());
            // Other fields like status, scheduledAt will be set by workflow/business logic

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );

            return idFuture.thenApply(id -> new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException ex) {
            CompletableFuture<TechnicalIdResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        } catch (Exception ex) {
            CompletableFuture<TechnicalIdResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    @GetMapping("/jobs/{technicalId}")
    @Operation(summary = "Retrieve a job by technicalId", description = "Retrieve job status and details by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Job found",
                    content = @Content(schema = @Schema(implementation = Job.class))),
            @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public CompletableFuture<Job> getJob(@Parameter(name = "technicalId", description = "Technical ID of the job") @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            return entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    uuid
            ).thenApply(json -> {
                // Convert ObjectNode to Job entity for response
                // Assuming ObjectNode can be converted to Job by Jackson or similar mapper
                // Here we will rely on a simple conversion, or just return a Job instance
                // For simplicity, assuming direct mapping is possible (in real scenario mapping needed)
                return json.traverse().readValueAs(Job.class);
            });
        } catch (IllegalArgumentException ex) {
            CompletableFuture<Job> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        } catch (Exception ex) {
            CompletableFuture<Job> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    // Laureate endpoints

    @GetMapping("/laureates/{technicalId}")
    @Operation(summary = "Retrieve a laureate by technicalId", description = "Retrieve laureate data by technicalId (assigned on ingestion)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Laureate found",
                    content = @Content(schema = @Schema(implementation = Laureate.class))),
            @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
            @ApiResponse(responseCode = "404", description = "Laureate not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public CompletableFuture<Laureate> getLaureate(@Parameter(name = "technicalId", description = "Technical ID of the laureate") @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            return entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    uuid
            ).thenApply(json -> {
                return json.traverse().readValueAs(Laureate.class);
            });
        } catch (IllegalArgumentException ex) {
            CompletableFuture<Laureate> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        } catch (Exception ex) {
            CompletableFuture<Laureate> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    // Subscriber endpoints

    @PostMapping("/subscribers")
    @Operation(summary = "Create a new subscriber", description = "Create a new subscriber that triggers subscriber creation event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscriber created successfully",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public CompletableFuture<TechnicalIdResponse> createSubscriber(@RequestBody SubscriberRequest request) {
        try {
            Subscriber subscriber = new Subscriber();
            subscriber.setContactType(request.getContactType());
            subscriber.setContactValue(request.getContactValue());
            subscriber.setActive(request.getActive());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    subscriber
            );

            return idFuture.thenApply(id -> new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException ex) {
            CompletableFuture<TechnicalIdResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        } catch (Exception ex) {
            CompletableFuture<TechnicalIdResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    @Operation(summary = "Retrieve a subscriber by technicalId", description = "Retrieve subscriber details by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscriber found",
                    content = @Content(schema = @Schema(implementation = Subscriber.class))),
            @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
            @ApiResponse(responseCode = "404", description = "Subscriber not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public CompletableFuture<Subscriber> getSubscriber(@Parameter(name = "technicalId", description = "Technical ID of the subscriber") @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            return entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    uuid
            ).thenApply(json -> {
                return json.traverse().readValueAs(Subscriber.class);
            });
        } catch (IllegalArgumentException ex) {
            CompletableFuture<Subscriber> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        } catch (Exception ex) {
            CompletableFuture<Subscriber> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    // Exception handlers for CompletableFuture exceptions

    @ExceptionHandler
    @ResponseStatus
    public void handleExceptions(Throwable ex) throws Throwable {
        if (ex instanceof IllegalArgumentException) {
            throw new IllegalArgumentException("Bad Request: " + ex.getMessage());
        } else if (ex instanceof ExecutionException) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                throw new NoSuchElementException("Not Found: " + cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                throw new IllegalArgumentException("Bad Request: " + cause.getMessage());
            } else {
                throw new RuntimeException("Internal Server Error: " + cause.getMessage());
            }
        } else {
            throw new RuntimeException("Internal Server Error: " + ex.getMessage());
        }
    }

    // Static DTO classes

    @Data
    @Schema(name = "JobRequest", description = "Request payload to create a Job")
    public static class JobRequest {
        @Schema(description = "API URL to ingest Nobel laureates data", example = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records", required = true)
        private String apiUrl;
    }

    @Data
    @Schema(name = "SubscriberRequest", description = "Request payload to create a Subscriber")
    public static class SubscriberRequest {
        @Schema(description = "Contact type e.g. email or webhook", example = "email", required = true)
        private String contactType;

        @Schema(description = "Contact value e.g. email address or webhook URL", example = "user@example.com", required = true)
        private String contactValue;

        @Schema(description = "Indicates if subscriber is active", example = "true", required = true)
        private Boolean active;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technical ID")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}