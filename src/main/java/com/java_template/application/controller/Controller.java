package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import lombok.Data;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;

import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api")
@Tag(name = "Controller", description = "Event-driven REST API Controller for Job, Laureate, and Subscriber entities")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST /jobs - Create Job entity
    @Operation(summary = "Create a new Job", description = "Creates a new Job entity and triggers the processJob workflow")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Job created successfully",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody JobCreateRequest request) {
        try {
            Job job = new Job();
            job.setJobName(request.getJobName());
            job.setStatus("SCHEDULED");
            job.setScheduledAt(java.time.OffsetDateTime.now().toString());
            job.setStartedAt(null);
            job.setCompletedAt(null);
            job.setResultSummary(null);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME, String.valueOf(Job.ENTITY_VERSION), job);

            UUID technicalId = idFuture.get();

            TechnicalIdResponse response = new TechnicalIdResponse();
            response.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Exception in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /jobs/{technicalId} - Retrieve Job entity by technicalId
    @Operation(summary = "Get Job by technicalId", description = "Retrieves Job entity and status by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Job retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Job.class))),
            @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(
            @Parameter(name = "technicalId", description = "Technical ID of the Job") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME, String.valueOf(Job.ENTITY_VERSION), id);

            ObjectNode jobNode = itemFuture.get();
            if (jobNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(jobNode);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in getJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Exception in getJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // POST /subscribers - Create Subscriber entity
    @Operation(summary = "Create a new Subscriber", description = "Creates a new Subscriber entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscriber created successfully",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody SubscriberCreateRequest request) {
        try {
            Subscriber subscriber = new Subscriber();
            subscriber.setContactType(request.getContactType());
            subscriber.setContactValue(request.getContactValue());
            subscriber.setActive(request.getActive());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME, String.valueOf(Subscriber.ENTITY_VERSION), subscriber);

            UUID technicalId = idFuture.get();

            TechnicalIdResponse response = new TechnicalIdResponse();
            response.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Exception in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /subscribers/{technicalId} - Retrieve Subscriber by technicalId
    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieves Subscriber details by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscriber retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Subscriber.class))),
            @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
            @ApiResponse(responseCode = "404", description = "Subscriber not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the Subscriber") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME, String.valueOf(Subscriber.ENTITY_VERSION), id);

            ObjectNode subscriberNode = itemFuture.get();
            if (subscriberNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(subscriberNode);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in getSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Exception in getSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /laureates/{technicalId} - Retrieve Laureate by technicalId
    @Operation(summary = "Get Laureate by technicalId", description = "Retrieves Laureate by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Laureate retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Laureate.class))),
            @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
            @ApiResponse(responseCode = "404", description = "Laureate not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the Laureate") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME, String.valueOf(Laureate.ENTITY_VERSION), id);

            ObjectNode laureateNode = itemFuture.get();
            if (laureateNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(laureateNode);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in getLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Exception in getLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity")
        private String technicalId;
    }

    @Data
    static class JobCreateRequest {
        @Schema(description = "Job name", example = "NobelLaureatesIngestionJob", required = true)
        private String jobName;
    }

    @Data
    static class SubscriberCreateRequest {
        @Schema(description = "Type of contact (e.g., email, webhook)", example = "email", required = true)
        private String contactType;

        @Schema(description = "Contact value (email address or webhook URL)", example = "user@example.com", required = true)
        private String contactValue;

        @Schema(description = "Indicates if subscriber is active", example = "true", required = true)
        private Boolean active;
    }
}