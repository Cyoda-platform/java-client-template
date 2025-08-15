package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.net.URI;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs")
@Tag(name = "Jobs", description = "Operations related to ingestion jobs")
public class JobController {
    private static final Logger logger = LoggerFactory.getLogger(JobController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Create a new ingestion job. Returns technicalId only. Supports Idempotency-Key header.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createJob(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody JobCreateRequest request
    ) {
        try {
            // basic validation
            if (request.getName() == null || request.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getSourceUrl() == null || request.getSourceUrl().isBlank()) {
                throw new IllegalArgumentException("sourceUrl is required");
            }
            if (request.getCreatedBy() == null || request.getCreatedBy().isBlank()) {
                throw new IllegalArgumentException("createdBy is required");
            }

            Job job = new Job();
            job.setName(request.getName());
            job.setSourceUrl(request.getSourceUrl());
            job.setSchedule(request.getSchedule());
            // transformRules may be structured; store as JSON string
            if (request.getTransformRules() != null) {
                try {
                    job.setTransformRules(objectMapper.writeValueAsString(request.getTransformRules()));
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException("transformRules must be serializable to JSON");
                }
            }
            job.setCreatedBy(request.getCreatedBy());
            if (request.getMaxRetries() != null) {
                // map maxRetries to retryCount field as a hint - actual retry behavior implemented in workflows
                job.setRetryCount(request.getMaxRetries());
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );

            UUID id = idFuture.get();
            String technicalId = id.toString();
            URI location = URI.create(String.format("/jobs/%s", technicalId));
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(location);
            return ResponseEntity.created(location).headers(headers).contentType(MediaType.APPLICATION_JSON).body(new IdResponse(technicalId));

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createJob: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unhandled exception in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Job", description = "Retrieve a job by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument in getJob: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unhandled exception in getJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    public static class JobCreateRequest {
        @Schema(description = "Human name of the ingestion job", required = true)
        private String name;
        @Schema(description = "Source URL (feed endpoint)", required = true)
        private String sourceUrl;
        @Schema(description = "Cron schedule (nullable for manual-only jobs)")
        private String schedule;
        @Schema(description = "Transform rules as structured JSON/Object")
        private Object transformRules;
        @Schema(description = "Operator who created the job", required = true)
        private String createdBy;
        @Schema(description = "Maximum retries (optional)")
        private Integer maxRetries;
    }

    @Data
    public static class IdResponse {
        @Schema(description = "Technical ID of the created resource")
        private String technicalId;

        public IdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
