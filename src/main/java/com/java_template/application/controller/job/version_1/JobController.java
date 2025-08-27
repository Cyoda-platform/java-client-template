package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/job")
@Tag(name = "Job", description = "Job entity API proxy - version 1")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Persist a Job entity and trigger orchestration. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job create request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateJobRequest.class)))
            @RequestBody CreateJobRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Job job = new Job();
            job.setId(request.getId());
            job.setSchedule(request.getSchedule());
            job.setSource(request.getSource());
            job.setScope(request.getScope());
            if (request.getSubscribersSnapshot() != null) {
                job.setSubscribersSnapshot(request.getSubscribersSnapshot());
            }

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in createJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Job by technicalId", description = "Retrieve a Job entity by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<JobResponse> getJobByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            JobResponse resp = objectMapper.treeToValue(node, JobResponse.class);
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getJobByTechnicalId: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in getJobByTechnicalId", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getJobByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Request/Response DTOs

    @Data
    @Schema(name = "CreateJobRequest", description = "Payload to create a Job")
    public static class CreateJobRequest {
        @Schema(description = "Business id supplied on create", example = "job-2025-08-27-01")
        private String id;

        @Schema(description = "Schedule descriptor (cron or manual)", example = "manual")
        private String schedule;

        @Schema(description = "Source API endpoint", example = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records")
        private String source;

        @Schema(description = "Scope (full incremental selective)", example = "incremental")
        private String scope;

        @Schema(description = "Snapshot of subscriber ids to notify", example = "[\"sub-1\",\"sub-2\"]")
        private List<String> subscribersSnapshot;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the persisted entity", example = "technicalId-abc123")
        private String technicalId;
    }

    @Data
    @Schema(name = "JobResponse", description = "Job read response")
    public static class JobResponse {
        @Schema(description = "Business id", example = "job-2025-08-27-01")
        private String id;

        @Schema(description = "Technical id", example = "technicalId-abc123")
        private String technicalId;

        @Schema(description = "Status", example = "NOTIFIED_SUBSCRIBERS")
        private String status;

        @Schema(description = "Started timestamp", example = "2025-08-27T12:00:00Z")
        private String startedAt;

        @Schema(description = "Finished timestamp", example = "2025-08-27T12:00:30Z")
        private String finishedAt;

        @Schema(description = "Result summary")
        private ResultSummaryDto resultSummary;

        @Schema(description = "List of error messages")
        private List<String> errorDetails;
    }

    @Data
    @Schema(name = "ResultSummary", description = "Summary of ingestion results")
    public static class ResultSummaryDto {
        @Schema(description = "Number ingested", example = "10")
        private Integer ingestedCount;

        @Schema(description = "Number updated", example = "2")
        private Integer updatedCount;

        @Schema(description = "Number of errors", example = "1")
        private Integer errorCount;
    }
}