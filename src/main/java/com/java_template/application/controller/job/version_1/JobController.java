package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Dull proxy controller for Job entity. All business logic must be implemented in workflows.
 */
@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Job Controller", description = "Proxy endpoints for Job entity (version 1)")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Create a Job entity. Returns only the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = CreateJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateJobRequest.class)))
            @RequestBody CreateJobRequest request
    ) {
        try {
            // Basic validation
            if (request == null) throw new IllegalArgumentException("Request body is required");
            if (request.getId() == null || request.getId().isBlank())
                throw new IllegalArgumentException("id is required");
            if (request.getApiEndpoint() == null || request.getApiEndpoint().isBlank())
                throw new IllegalArgumentException("apiEndpoint is required");
            if (request.getSchedule() == null || request.getSchedule().isBlank())
                throw new IllegalArgumentException("schedule is required");

            Job job = new Job();
            job.setId(request.getId());
            job.setApiEndpoint(request.getApiEndpoint());
            job.setSchedule(request.getSchedule());
            job.setLastError(request.getLastError());
            job.setStartedAt(request.getStartedAt());
            job.setFinishedAt(request.getFinishedAt());
            job.setCreatedAt(request.getCreatedAt() != null && !request.getCreatedAt().isBlank() ? request.getCreatedAt() : Instant.now().toString());
            job.setAttempts(request.getAttempts() != null ? request.getAttempts() : 0);
            job.setState(request.getState() != null && !request.getState().isBlank() ? request.getState() : "SCHEDULED");

            // Persist via EntityService
            var idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();
            CreateJobResponse response = new CreateJobResponse();
            response.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create Job: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException creating Job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating Job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error creating Job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Bulk create Jobs", description = "Create multiple Job entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = CreateJobsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/bulk", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createJobsBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Bulk job creation payload", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateJobRequest.class))))
            @RequestBody List<CreateJobRequest> requests
    ) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request body is required and must contain elements");

            List<Job> jobs = new ArrayList<>();
            for (CreateJobRequest request : requests) {
                if (request == null) throw new IllegalArgumentException("Each request must be non-null");
                if (request.getId() == null || request.getId().isBlank())
                    throw new IllegalArgumentException("id is required for each job");
                if (request.getApiEndpoint() == null || request.getApiEndpoint().isBlank())
                    throw new IllegalArgumentException("apiEndpoint is required for each job");
                if (request.getSchedule() == null || request.getSchedule().isBlank())
                    throw new IllegalArgumentException("schedule is required for each job");

                Job job = new Job();
                job.setId(request.getId());
                job.setApiEndpoint(request.getApiEndpoint());
                job.setSchedule(request.getSchedule());
                job.setLastError(request.getLastError());
                job.setStartedAt(request.getStartedAt());
                job.setFinishedAt(request.getFinishedAt());
                job.setCreatedAt(request.getCreatedAt() != null && !request.getCreatedAt().isBlank() ? request.getCreatedAt() : Instant.now().toString());
                job.setAttempts(request.getAttempts() != null ? request.getAttempts() : 0);
                job.setState(request.getState() != null && !request.getState().isBlank() ? request.getState() : "SCHEDULED");
                jobs.add(job);
            }

            var idsFuture = entityService.addItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    jobs
            );
            List<UUID> ids = idsFuture.get();
            CreateJobsResponse resp = new CreateJobsResponse();
            List<String> technicalIds = new ArrayList<>();
            for (UUID id : ids) technicalIds.add(id.toString());
            resp.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid bulk create request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException bulk creating Jobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while bulk creating Jobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error bulk creating Jobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all Jobs", description = "Retrieve all Job entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAllJobs() {
        try {
            var itemsFuture = entityService.getItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION)
            );
            ArrayNode arrayNode = itemsFuture.get();
            List<JobResponse> responses = new ArrayList<>();
            if (arrayNode != null) {
                for (int i = 0; i < arrayNode.size(); i++) {
                    if (arrayNode.get(i).isObject()) {
                        responses.add(mapObjectNodeToJobResponse((ObjectNode) arrayNode.get(i)));
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException fetching Jobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching Jobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error fetching Jobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Filter Jobs", description = "Filter Jobs by simple field condition. Provide field, operator and value as query parameters.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/filter", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> filterJobs(
            @Parameter(description = "Field to filter on (e.g. $.state)", required = true) @RequestParam(name = "field") String field,
            @Parameter(description = "Operator (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.)", required = true) @RequestParam(name = "operator") String operator,
            @Parameter(description = "Value to compare", required = true) @RequestParam(name = "value") String value
    ) {
        try {
            if (field == null || field.isBlank() || operator == null || operator.isBlank() || value == null)
                throw new IllegalArgumentException("field, operator and value are required");

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of(field, operator, value)
            );

            var filteredItemsFuture = entityService.getItemsByCondition(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode arrayNode = filteredItemsFuture.get();
            List<JobResponse> responses = new ArrayList<>();
            if (arrayNode != null) {
                for (int i = 0; i < arrayNode.size(); i++) {
                    if (arrayNode.get(i).isObject()) {
                        responses.add(mapObjectNodeToJobResponse((ObjectNode) arrayNode.get(i)));
                    }
                }
            }
            return ResponseEntity.ok(responses);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid filter request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException filtering Jobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while filtering Jobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error filtering Jobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Job by technicalId", description = "Retrieve a single Job by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getJobByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            var itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            JobResponse resp = mapObjectNodeToJobResponse(node);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid get request for Job: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException fetching Job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching Job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error fetching Job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Job", description = "Update a Job entity by technicalId. Returns the updated technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated", content = @Content(schema = @Schema(implementation = CreateJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job update payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateJobRequest.class)))
            @RequestBody CreateJobRequest request
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Job job = new Job();
            // map only provided fields; do not impose business rules here
            job.setId(request.getId());
            job.setApiEndpoint(request.getApiEndpoint());
            job.setSchedule(request.getSchedule());
            job.setLastError(request.getLastError());
            job.setStartedAt(request.getStartedAt());
            job.setFinishedAt(request.getFinishedAt());
            job.setCreatedAt(request.getCreatedAt());
            job.setAttempts(request.getAttempts());
            job.setState(request.getState());

            var updatedIdFuture = entityService.updateItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    job
            );
            UUID updated = updatedIdFuture.get();
            CreateJobResponse resp = new CreateJobResponse();
            resp.setTechnicalId(updated.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid update request for Job: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException updating Job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating Job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error updating Job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Job", description = "Delete a Job entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(implementation = CreateJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            var deletedFuture = entityService.deleteItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID deleted = deletedFuture.get();
            CreateJobResponse resp = new CreateJobResponse();
            resp.setTechnicalId(deleted.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid delete request for Job: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException deleting Job", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting Job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error deleting Job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Helper to map ObjectNode returned by EntityService to JobResponse DTO
    private JobResponse mapObjectNodeToJobResponse(ObjectNode node) {
        JobResponse resp = new JobResponse();
        if (node.has("technicalId")) resp.setTechnicalId(node.get("technicalId").asText(null));
        if (node.has("id")) resp.setId(node.get("id").asText(null));
        if (node.has("schedule")) resp.setSchedule(node.get("schedule").asText(null));
        if (node.has("apiEndpoint")) resp.setApiEndpoint(node.get("apiEndpoint").asText(null));
        if (node.has("state")) resp.setState(node.get("state").asText(null));
        if (node.has("startedAt")) resp.setStartedAt(node.get("startedAt").asText(null));
        if (node.has("finishedAt")) resp.setFinishedAt(node.get("finishedAt").asText(null));
        if (node.has("lastError")) resp.setLastError(node.get("lastError").isNull() ? null : node.get("lastError").asText(null));
        if (node.has("createdAt")) resp.setCreatedAt(node.get("createdAt").asText(null));
        if (node.has("attempts")) {
            try {
                resp.setAttempts(node.get("attempts").isNull() ? null : node.get("attempts").asInt());
            } catch (Exception ignore) {
            }
        }
        return resp;
    }

    // Request and Response DTOs (static classes within controller)
    @Data
    @Schema(name = "CreateJobRequest", description = "Payload to create or update a Job")
    public static class CreateJobRequest {
        @Schema(description = "Business id from client", required = true, example = "job-01")
        private String id;

        @Schema(description = "Cron or interval expression", required = true, example = "0 0 * * *")
        private String schedule;

        @Schema(description = "Nobel laureates API URL", required = true, example = "https://public.opendatasoft.com/api/...")
        private String apiEndpoint;

        @Schema(description = "Job state (SCHEDULED, INGESTING, SUCCEEDED, FAILED, NOTIFIED_SUBSCRIBERS)", example = "SCHEDULED")
        private String state;

        @Schema(description = "Timestamp when job started", example = "2025-08-26T10:00:00Z")
        private String startedAt;

        @Schema(description = "Timestamp when job finished", example = "2025-08-26T10:00:10Z")
        private String finishedAt;

        @Schema(description = "Retry attempts", example = "0")
        private Integer attempts;

        @Schema(description = "Last error message", example = "Timeout while fetching")
        private String lastError;

        @Schema(description = "Creation timestamp", example = "2025-08-26T10:00:00Z")
        private String createdAt;
    }

    @Data
    @Schema(name = "CreateJobResponse", description = "Response containing technicalId only")
    public static class CreateJobResponse {
        @Schema(description = "Technical ID of the entity", example = "tx-job-0001")
        private String technicalId;
    }

    @Data
    @Schema(name = "CreateJobsResponse", description = "Response for bulk create containing technicalIds")
    public static class CreateJobsResponse {
        @Schema(description = "List of technical IDs", example = "[\"tx-job-0001\",\"tx-job-0002\"]")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "JobResponse", description = "Job representation returned by GET endpoints")
    public static class JobResponse {
        @Schema(description = "Technical ID of the entity", example = "tx-job-0001")
        private String technicalId;

        @Schema(description = "Business id from client", example = "job-01")
        private String id;

        @Schema(description = "Cron or interval expression", example = "0 0 * * *")
        private String schedule;

        @Schema(description = "Nobel laureates API URL", example = "https://public.opendatasoft.com/api/...")
        private String apiEndpoint;

        @Schema(description = "Job state", example = "NOTIFIED_SUBSCRIBERS")
        private String state;

        @Schema(description = "Timestamp when job started", example = "2025-08-26T10:00:00Z")
        private String startedAt;

        @Schema(description = "Timestamp when job finished", example = "2025-08-26T10:00:10Z")
        private String finishedAt;

        @Schema(description = "Retry attempts", example = "1")
        private Integer attempts;

        @Schema(description = "Last error message", example = "Parsing error")
        private String lastError;

        @Schema(description = "Creation timestamp", example = "2025-08-26T09:59:59Z")
        private String createdAt;
    }
}