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
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api")
@Tag(name = "Event-Driven Controller", description = "Controller handling event-driven entity persistence and retrieval")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // --- JOB ENDPOINTS ---

    @PostMapping("/jobs")
    @Operation(summary = "Create a new Job", description = "Creates a new Job entity with status SCHEDULED and returns its technical ID")
    @ApiResponse(responseCode = "200", description = "Job created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    public ResponseEntity<?> createJob(@RequestBody JobCreateRequest request) {
        try {
            Job job = new Job();
            job.setJobName(request.getJobName());
            // Other fields like status, createdAt, etc. should be set by workflow/process, not controller
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse response = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.ok(response);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createJob", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Exception in createJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @GetMapping("/jobs/{technicalId}")
    @Operation(summary = "Get Job by technicalId", description = "Retrieves a Job entity by its technical ID")
    @ApiResponse(responseCode = "200", description = "Job found", content = @Content(schema = @Schema(implementation = JobResponse.class)))
    public ResponseEntity<?> getJobById(@Parameter(name = "technicalId", description = "Technical ID of the Job") @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            JobResponse jobResponse = JsonUtil.convert(node, JobResponse.class);
            return ResponseEntity.ok(jobResponse);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getJobById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Exception in getJobById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // --- SUBSCRIBER ENDPOINTS ---

    @PostMapping("/subscribers")
    @Operation(summary = "Create a new Subscriber", description = "Creates a new Subscriber entity and returns its technical ID")
    @ApiResponse(responseCode = "200", description = "Subscriber created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    public ResponseEntity<?> createSubscriber(@RequestBody SubscriberCreateRequest request) {
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
            UUID technicalId = idFuture.get();
            TechnicalIdResponse response = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.ok(response);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createSubscriber", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Exception in createSubscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @GetMapping("/subscribers/{technicalId}")
    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieves a Subscriber entity by its technical ID")
    @ApiResponse(responseCode = "200", description = "Subscriber found", content = @Content(schema = @Schema(implementation = SubscriberResponse.class)))
    public ResponseEntity<?> getSubscriberById(@Parameter(name = "technicalId", description = "Technical ID of the Subscriber") @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            SubscriberResponse response = JsonUtil.convert(node, SubscriberResponse.class);
            return ResponseEntity.ok(response);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getSubscriberById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Exception in getSubscriberById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // --- LAUREATE ENDPOINTS ---

    @GetMapping("/laureates/{technicalId}")
    @Operation(summary = "Get Laureate by technicalId", description = "Retrieves a Laureate entity by its technical ID")
    @ApiResponse(responseCode = "200", description = "Laureate found", content = @Content(schema = @Schema(implementation = LaureateResponse.class)))
    public ResponseEntity<?> getLaureateById(@Parameter(name = "technicalId", description = "Technical ID of the Laureate") @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            LaureateResponse response = JsonUtil.convert(node, LaureateResponse.class);
            return ResponseEntity.ok(response);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLaureateById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Exception in getLaureateById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    public static class JobCreateRequest {
        @Schema(description = "Name/identifier of the ingestion job", example = "exampleJobName", required = true)
        private String jobName;
    }

    @Data
    public static class JobResponse {
        @Schema(description = "Name/identifier of the ingestion job")
        private String jobName;

        @Schema(description = "Current state of the job")
        private String status;

        @Schema(description = "Timestamp of job creation")
        private String createdAt;

        @Schema(description = "Timestamp of job completion, if applicable")
        private String completedAt;

        @Schema(description = "Error details if the job failed")
        private String errorMessage;
    }

    @Data
    public static class SubscriberCreateRequest {
        @Schema(description = "Type of contact (email or webhook)", example = "email", required = true)
        private String contactType;

        @Schema(description = "Email address or webhook URL", example = "user@example.com", required = true)
        private String contactValue;

        @Schema(description = "Subscription active status", required = true)
        private Boolean active;
    }

    @Data
    public static class SubscriberResponse {
        @Schema(description = "Type of contact")
        private String contactType;

        @Schema(description = "Email address or webhook URL")
        private String contactValue;

        @Schema(description = "Subscription active status")
        private Boolean active;
    }

    @Data
    public static class LaureateResponse {
        @Schema(description = "Unique identifier from source data")
        private String laureateId;

        @Schema(description = "Laureate first name")
        private String firstname;

        @Schema(description = "Laureate surname")
        private String surname;

        @Schema(description = "Gender of laureate")
        private String gender;

        @Schema(description = "Birthdate")
        private String born;

        @Schema(description = "Date of death, nullable")
        private String died;

        @Schema(description = "Country of birth")
        private String borncountry;

        @Schema(description = "Country code of birth")
        private String borncountrycode;

        @Schema(description = "City of birth")
        private String borncity;

        @Schema(description = "Award year")
        private String year;

        @Schema(description = "Award category")
        private String category;

        @Schema(description = "Award motivation text")
        private String motivation;

        @Schema(description = "Affiliation name")
        private String name;

        @Schema(description = "Affiliation city")
        private String city;

        @Schema(description = "Affiliation country")
        private String country;
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity")
        private final String technicalId;
    }

    // Utility class for JSON conversions (using Jackson ObjectMapper)
    private static class JsonUtil {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static <T> T convert(ObjectNode node, Class<T> clazz) throws com.fasterxml.jackson.core.JsonProcessingException {
            return mapper.treeToValue(node, clazz);
        }
    }
}