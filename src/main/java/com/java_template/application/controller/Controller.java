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
@Tag(name = "Nobel Laureates Data Ingestion Backend Controller", description = "Event-driven REST API controller for Nobel Laureates ingestion backend")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // --------- JOB ENDPOINTS ---------

    @Operation(summary = "Create a new Job", description = "Create a Job entity with status SCHEDULED")
    @ApiResponse(responseCode = "200", description = "Job created",
            content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody JobCreateRequest request) {
        try {
            Job job = new Job();
            job.setJobName(request.getJobName());
            job.setScheduleInfo(request.getScheduleInfo());
            job.setStatus("SCHEDULED");
            // createdAt and completedAt managed by workflows or persistence layer

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );
            UUID technicalId = idFuture.get();

            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in createJob", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception in createJob", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in createJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get a Job by technicalId", description = "Retrieve a Job entity by its technicalId")
    @ApiResponse(responseCode = "200", description = "Job retrieved",
            content = @Content(schema = @Schema(implementation = JobResponse.class)))
    @Parameter(name = "technicalId", description = "Technical ID of the Job", required = true)
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJobById(@PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();

            JobResponse response = new JobResponse();
            response.setJobName(node.path("jobName").asText(null));
            response.setStatus(node.path("status").asText(null));
            response.setScheduleInfo(node.path("scheduleInfo").asText(null));
            response.setCreatedAt(node.path("createdAt").asText(null));
            if (node.hasNonNull("completedAt")) {
                response.setCompletedAt(node.path("completedAt").asText(null));
            } else {
                response.setCompletedAt(null);
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in getJobById", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception in getJobById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in getJobById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // --------- LAUREATE ENDPOINTS ---------

    @Operation(summary = "Get a Laureate by technicalId", description = "Retrieve a Laureate entity by its technicalId")
    @ApiResponse(responseCode = "200", description = "Laureate retrieved",
            content = @Content(schema = @Schema(implementation = LaureateResponse.class)))
    @Parameter(name = "technicalId", description = "Technical ID of the Laureate", required = true)
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureateById(@PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();

            LaureateResponse response = new LaureateResponse();
            response.setLaureateId(node.path("laureateId").isInt() ? node.get("laureateId").intValue() : null);
            response.setFirstname(node.path("firstname").asText(null));
            response.setSurname(node.path("surname").asText(null));
            response.setBorn(node.path("born").asText(null));
            response.setDied(node.hasNonNull("died") ? node.path("died").asText(null) : null);
            response.setBorncountry(node.path("borncountry").asText(null));
            response.setBorncountrycode(node.path("borncountrycode").asText(null));
            response.setBorncity(node.path("borncity").asText(null));
            response.setGender(node.path("gender").asText(null));
            response.setYear(node.path("year").asText(null));
            response.setCategory(node.path("category").asText(null));
            response.setMotivation(node.path("motivation").asText(null));
            response.setAffiliationName(node.path("affiliationName").asText(null));
            response.setAffiliationCity(node.path("affiliationCity").asText(null));
            response.setAffiliationCountry(node.path("affiliationCountry").asText(null));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in getLaureateById", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception in getLaureateById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in getLaureateById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // --------- SUBSCRIBER ENDPOINTS ---------

    @Operation(summary = "Create a new Subscriber", description = "Create a Subscriber entity immutably")
    @ApiResponse(responseCode = "200", description = "Subscriber created",
            content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody SubscriberCreateRequest request) {
        try {
            Subscriber subscriber = new Subscriber();
            subscriber.setContactType(request.getContactType());
            subscriber.setContactValue(request.getContactValue());
            subscriber.setPreferences(request.getPreferences());
            // subscribedAt managed by workflows or persistence layer

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    subscriber
            );
            UUID technicalId = idFuture.get();

            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in createSubscriber", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception in createSubscriber", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in createSubscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get a Subscriber by technicalId", description = "Retrieve a Subscriber entity by its technicalId")
    @ApiResponse(responseCode = "200", description = "Subscriber retrieved",
            content = @Content(schema = @Schema(implementation = SubscriberResponse.class)))
    @Parameter(name = "technicalId", description = "Technical ID of the Subscriber", required = true)
    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriberById(@PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();

            SubscriberResponse response = new SubscriberResponse();
            response.setContactType(node.path("contactType").asText(null));
            response.setContactValue(node.path("contactValue").asText(null));
            response.setPreferences(node.path("preferences").asText(null));
            response.setSubscribedAt(node.path("subscribedAt").asText(null));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in getSubscriberById", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception in getSubscriberById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in getSubscriberById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // --------- DTO CLASSES ---------

    @lombok.Data
    static class JobCreateRequest {
        @Schema(description = "Job name", example = "Ingestion Job 1", required = true)
        private String jobName;

        @Schema(description = "Optional schedule info", example = "Daily at midnight")
        private String scheduleInfo;
    }

    @lombok.Data
    static class JobResponse {
        @Schema(description = "Job name", example = "Ingestion Job 1")
        private String jobName;

        @Schema(description = "Job status", example = "SCHEDULED")
        private String status;

        @Schema(description = "Schedule info", example = "Daily at midnight")
        private String scheduleInfo;

        @Schema(description = "Creation timestamp (ISO datetime)", example = "2024-01-01T00:00:00Z")
        private String createdAt;

        @Schema(description = "Completion timestamp (ISO datetime or null)", example = "2024-01-02T00:00:00Z")
        private String completedAt;
    }

    @lombok.Data
    static class LaureateResponse {
        @Schema(description = "Laureate ID", example = "123")
        private Integer laureateId;

        @Schema(description = "First name", example = "Marie")
        private String firstname;

        @Schema(description = "Surname", example = "Curie")
        private String surname;

        @Schema(description = "Birth date (ISO date)", example = "1867-11-07")
        private String born;

        @Schema(description = "Death date (ISO date or null)", example = "1934-07-04")
        private String died;

        @Schema(description = "Birth country", example = "Poland")
        private String borncountry;

        @Schema(description = "Birth country code", example = "PL")
        private String borncountrycode;

        @Schema(description = "Birth city", example = "Warsaw")
        private String borncity;

        @Schema(description = "Gender", example = "Female")
        private String gender;

        @Schema(description = "Award year", example = "1911")
        private String year;

        @Schema(description = "Nobel Prize category", example = "Chemistry")
        private String category;

        @Schema(description = "Award motivation", example = "For her discovery of radium")
        private String motivation;

        @Schema(description = "Affiliation institution name", example = "Sorbonne")
        private String affiliationName;

        @Schema(description = "Affiliation city", example = "Paris")
        private String affiliationCity;

        @Schema(description = "Affiliation country", example = "France")
        private String affiliationCountry;
    }

    @lombok.Data
    static class SubscriberCreateRequest {
        @Schema(description = "Contact type (email, webhook, etc.)", example = "email", required = true)
        private String contactType;

        @Schema(description = "Contact address or URL", example = "user@example.com", required = true)
        private String contactValue;

        @Schema(description = "Optional notification preferences", example = "daily")
        private String preferences;
    }

    @lombok.Data
    static class SubscriberResponse {
        @Schema(description = "Contact type", example = "email")
        private String contactType;

        @Schema(description = "Contact address or URL", example = "user@example.com")
        private String contactValue;

        @Schema(description = "Notification preferences", example = "daily")
        private String preferences;

        @Schema(description = "Subscription timestamp (ISO datetime)", example = "2024-01-01T00:00:00Z")
        private String subscribedAt;
    }

    @lombok.Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical ID", example = "123e4567-e89b-12d3-a456-426614174000")
        private final String technicalId;
    }
}