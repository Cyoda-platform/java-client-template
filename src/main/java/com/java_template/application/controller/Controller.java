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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api")
@Tag(name = "Controller", description = "Dull event-driven REST API controller for entity persistence and workflow triggering")
public class Controller {

    private final EntityService entityService;
    private final Logger logger = LoggerFactory.getLogger(Controller.class);

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST /jobs - Create Job entity
    @PostMapping("/jobs")
    @Operation(summary = "Create Job", description = "Create a new Job entity with status PENDING")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Job created",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<?> createJob(@RequestBody JobRequest request) {
        try {
            Job job = new Job();
            job.setJobName(request.getJobName());
            job.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();

            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));

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
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /jobs/{technicalId} - Retrieve Job entity by technicalId
    @GetMapping("/jobs/{technicalId}")
    @Operation(summary = "Get Job by technicalId", description = "Retrieve Job entity by its technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Job found",
                    content = @Content(schema = @Schema(implementation = Job.class))),
            @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<?> getJobByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the Job")
            @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode jobNode = itemFuture.get();
            return ResponseEntity.ok(jobNode);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getJobByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getJobByTechnicalId", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in getJobByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /laureates/{technicalId} - Retrieve Laureate entity by technicalId
    @GetMapping("/laureates/{technicalId}")
    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve Laureate entity by its technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Laureate found",
                    content = @Content(schema = @Schema(implementation = Laureate.class))),
            @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
            @ApiResponse(responseCode = "404", description = "Laureate not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<?> getLaureateByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the Laureate")
            @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode laureateNode = itemFuture.get();
            return ResponseEntity.ok(laureateNode);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getLaureateByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLaureateByTechnicalId", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in getLaureateByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // POST /subscribers - Create Subscriber entity
    @PostMapping("/subscribers")
    @Operation(summary = "Create Subscriber", description = "Create a new Subscriber entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscriber created",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<?> createSubscriber(@RequestBody SubscriberRequest request) {
        try {
            Subscriber subscriber = new Subscriber();
            subscriber.setSubscriberName(request.getSubscriberName());
            subscriber.setContactType(request.getContactType());
            subscriber.setContactDetails(request.getContactDetails());
            subscriber.setSubscribedCategories(request.getSubscribedCategories());
            subscriber.setActive(request.getActive());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    subscriber
            );

            UUID technicalId = idFuture.get();

            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));

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
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /subscribers/{technicalId} - Retrieve Subscriber entity by technicalId
    @GetMapping("/subscribers/{technicalId}")
    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve Subscriber entity by its technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscriber found",
                    content = @Content(schema = @Schema(implementation = Subscriber.class))),
            @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
            @ApiResponse(responseCode = "404", description = "Subscriber not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<?> getSubscriberByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the Subscriber")
            @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode subscriberNode = itemFuture.get();
            return ResponseEntity.ok(subscriberNode);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getSubscriberByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getSubscriberByTechnicalId", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in getSubscriberByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity", example = "uuid-string")
        private final String technicalId;
    }

    @Data
    public static class JobRequest {
        @Schema(description = "Name of the ingestion job", example = "Nobel Laureates Ingestion", required = true)
        private String jobName;
    }

    @Data
    public static class SubscriberRequest {
        @Schema(description = "Name of the subscriber", example = "Science Weekly", required = true)
        private String subscriberName;

        @Schema(description = "Contact type (e.g., email, webhook URL)", example = "email", required = true)
        private String contactType;

        @Schema(description = "Contact details (email address or webhook endpoint)", example = "alerts@scienceweekly.com", required = true)
        private String contactDetails;

        @Schema(description = "Comma-separated list of Nobel Prize categories", example = "Physics,Chemistry", required = true)
        private String subscribedCategories;

        @Schema(description = "Subscriber active status", example = "true", required = true)
        private Boolean active;
    }
}