package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.job.version_1.Job;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs")
@Tag(name = "Job Controller", description = "Event-driven endpoints for Jobs (orchestration)")
public class JobController {
    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Create a job orchestration record. Returns a technicalId immediately.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createJob(@RequestBody JobCreateRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            if (request.getJobType() == null || request.getJobType().isBlank()) {
                throw new IllegalArgumentException("jobType is required");
            }

            Job job = new Job();
            String jobId = UUID.randomUUID().toString();
            job.setJobId(jobId);
            job.setJobType(request.getJobType());
            job.setSchedule(request.getSchedule());
            job.setParameters(request.getParameters() == null ? null : request.getParameters().toString());
            job.setStatus("PENDING");
            job.setStartedAt(null);
            job.setFinishedAt(null);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                Job.ENTITY_NAME,
                String.valueOf(Job.ENTITY_VERSION),
                job
            );

            UUID storedId = idFuture.get();
            logger.info("Created Job entity stored with id: {} (entity-store id) and jobId: {}", storedId, jobId);

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(jobId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Validation error creating job: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating job", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error creating job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Job", description = "Retrieve a job by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(value = "/{technicalId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getJob(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Job.ENTITY_NAME,
                String.valueOf(Job.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            Job job = objectMapper.treeToValue(node, Job.class);

            JobResponse resp = new JobResponse();
            resp.setJobId(job.getJobId());
            resp.setJobType(job.getJobType());
            resp.setSchedule(job.getSchedule());
            resp.setStatus(job.getStatus());
            resp.setParameters(job.getParameters());
            resp.setStartedAt(job.getStartedAt());
            resp.setFinishedAt(job.getFinishedAt());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Validation error getting job: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching job", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error fetching job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Data
    static class JobCreateRequest {
        @Schema(description = "Type of the job (INGESTION | WEEKLY_REPORT | RECOMMENDATION_RUN)")
        private String jobType;
        @Schema(description = "Optional schedule (cron)")
        private String schedule;
        @Schema(description = "Optional parameters (free-form JSON)")
        private ObjectNode parameters;
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical id of created entity")
        private String technicalId;
    }

    @Data
    static class JobResponse {
        @Schema(description = "Job technical id")
        private String jobId;
        @Schema(description = "Job type")
        private String jobType;
        @Schema(description = "Schedule (optional)")
        private String schedule;
        @Schema(description = "Status")
        private String status;
        @Schema(description = "Parameters (JSON string)")
        private String parameters;
        @Schema(description = "Started at timestamp")
        private String startedAt;
        @Schema(description = "Finished at timestamp")
        private String finishedAt;
    }
}
