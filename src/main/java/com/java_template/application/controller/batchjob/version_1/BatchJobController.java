package com.java_template.application.controller.batchjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.batchjob.version_1.BatchJob;
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

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Dull proxy controller for BatchJob entity. All business logic is implemented in workflows.
 */
@RestController
@RequestMapping("/api/v1/batchjobs")
@Tag(name = "BatchJob", description = "Operations for BatchJob entity (proxy to entity service)")
public class BatchJobController {

    private static final Logger logger = LoggerFactory.getLogger(BatchJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BatchJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create a BatchJob", description = "Creates a new BatchJob entity. Controller only proxies to EntityService.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createJob(@RequestBody CreateJobRequest request) {
        try {
            // Basic validation of request format
            if (request == null) throw new IllegalArgumentException("Request body is required");
            if (request.getJobName() == null || request.getJobName().isBlank()) throw new IllegalArgumentException("job_name is required");
            if (request.getApiEndpoint() == null || request.getApiEndpoint().isBlank()) throw new IllegalArgumentException("api_endpoint is required");
            if (request.getScheduleCron() == null || request.getScheduleCron().isBlank()) throw new IllegalArgumentException("schedule_cron is required");
            if (request.getAdminEmails() == null || request.getAdminEmails().isEmpty()) throw new IllegalArgumentException("admin_emails is required");

            BatchJob data = new BatchJob();
            data.setJobName(request.getJobName());
            data.setApiEndpoint(request.getApiEndpoint());
            data.setScheduleCron(request.getScheduleCron());
            data.setAdminEmails(request.getAdminEmails());
            // minimal fields required by entity validation
            data.setCreatedAt(java.time.OffsetDateTime.now().toString());
            data.setStatus("PENDING");
            data.setMetadata(request.getMetadata());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION),
                    data
            );
            UUID id = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted creating job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple BatchJobs (bulk)", description = "Creates multiple BatchJob entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createJobsBulk(@RequestBody List<CreateJobRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request list is required");
            List<BatchJob> entities = new ArrayList<>();
            for (CreateJobRequest req : requests) {
                if (req == null) throw new IllegalArgumentException("Each request must be provided");
                if (req.getJobName() == null || req.getJobName().isBlank()) throw new IllegalArgumentException("job_name is required for each item");
                if (req.getApiEndpoint() == null || req.getApiEndpoint().isBlank()) throw new IllegalArgumentException("api_endpoint is required for each item");
                if (req.getScheduleCron() == null || req.getScheduleCron().isBlank()) throw new IllegalArgumentException("schedule_cron is required for each item");
                if (req.getAdminEmails() == null || req.getAdminEmails().isEmpty()) throw new IllegalArgumentException("admin_emails is required for each item");

                BatchJob data = new BatchJob();
                data.setJobName(req.getJobName());
                data.setApiEndpoint(req.getApiEndpoint());
                data.setScheduleCron(req.getScheduleCron());
                data.setAdminEmails(req.getAdminEmails());
                data.setCreatedAt(java.time.OffsetDateTime.now().toString());
                data.setStatus("PENDING");
                data.setMetadata(req.getMetadata());
                entities.add(data);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<TechnicalIdResponse> resp = new ArrayList<>();
            for (UUID id : ids) {
                TechnicalIdResponse tr = new TechnicalIdResponse();
                tr.setTechnicalId(id.toString());
                resp.add(tr);
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createJobsBulk: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createJobsBulk", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted creating jobs bulk", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createJobsBulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get a BatchJob by technicalId", description = "Retrieves a BatchJob entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            if (node == null) throw new NoSuchElementException("BatchJob not found");
            BatchJob bj = objectMapper.convertValue(node, BatchJob.class);
            GetJobResponse resp = new GetJobResponse();
            resp.setTechnicalId(technicalId);
            resp.setJobName(bj.getJobName());
            resp.setStatus(bj.getStatus());
            resp.setLastRunTimestamp(bj.getLastRunTimestamp());
            resp.setMetadata(bj.getMetadata());
            resp.setApiEndpoint(bj.getApiEndpoint());
            resp.setScheduleCron(bj.getScheduleCron());
            resp.setAdminEmails(bj.getAdminEmails());
            resp.setCreatedAt(bj.getCreatedAt());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted fetching job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all BatchJobs", description = "Retrieves all BatchJob entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = GetJobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAllJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            List<GetJobResponse> resp = new ArrayList<>();
            if (arr != null) {
                for (JsonNode node : arr) {
                    BatchJob bj = objectMapper.convertValue(node, BatchJob.class);
                    GetJobResponse gr = new GetJobResponse();
                    gr.setTechnicalId(node.has("technicalId") ? node.get("technicalId").asText() : null);
                    gr.setJobName(bj.getJobName());
                    gr.setStatus(bj.getStatus());
                    gr.setLastRunTimestamp(bj.getLastRunTimestamp());
                    gr.setMetadata(bj.getMetadata());
                    gr.setApiEndpoint(bj.getApiEndpoint());
                    gr.setScheduleCron(bj.getScheduleCron());
                    gr.setAdminEmails(bj.getAdminEmails());
                    gr.setCreatedAt(bj.getCreatedAt());
                    resp.add(gr);
                }
            }
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in getAllJobs", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted fetching all jobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getAllJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search BatchJobs by condition", description = "Retrieves BatchJob entities that match the provided search condition.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = GetJobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchJobs(@RequestBody SearchConditionRequest conditionRequest) {
        try {
            if (conditionRequest == null) throw new IllegalArgumentException("Condition request is required");
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION),
                    conditionRequest,
                    true
            );
            ArrayNode arr = filteredItemsFuture.get();
            List<GetJobResponse> resp = new ArrayList<>();
            if (arr != null) {
                for (JsonNode node : arr) {
                    BatchJob bj = objectMapper.convertValue(node, BatchJob.class);
                    GetJobResponse gr = new GetJobResponse();
                    gr.setTechnicalId(node.has("technicalId") ? node.get("technicalId").asText() : null);
                    gr.setJobName(bj.getJobName());
                    gr.setStatus(bj.getStatus());
                    gr.setLastRunTimestamp(bj.getLastRunTimestamp());
                    gr.setMetadata(bj.getMetadata());
                    gr.setApiEndpoint(bj.getApiEndpoint());
                    gr.setScheduleCron(bj.getScheduleCron());
                    gr.setAdminEmails(bj.getAdminEmails());
                    gr.setCreatedAt(bj.getCreatedAt());
                    resp.add(gr);
                }
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in searchJobs", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted searching jobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in searchJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update a BatchJob", description = "Updates an existing BatchJob entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(value = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId,
            @RequestBody UpdateJobRequest request
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");
            UUID id = UUID.fromString(technicalId);

            BatchJob data = new BatchJob();
            // For update we expect client to provide necessary fields; controller is a dumb proxy.
            data.setJobName(request.getJobName());
            data.setApiEndpoint(request.getApiEndpoint());
            data.setScheduleCron(request.getScheduleCron());
            data.setAdminEmails(request.getAdminEmails());
            data.setCreatedAt(request.getCreatedAt());
            data.setStatus(request.getStatus());
            data.setLastRunTimestamp(request.getLastRunTimestamp());
            data.setMetadata(request.getMetadata());

            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION),
                    id,
                    data
            );
            UUID resId = updatedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(resId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid updateJob request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted updating job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in updateJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete a BatchJob", description = "Deletes a BatchJob entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION),
                    id
            );
            UUID res = deletedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(res.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid deleteJob request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted deleting job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in deleteJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateJobRequest", description = "Request payload to create a BatchJob")
    public static class CreateJobRequest {
        @Schema(name = "job_name", description = "Human name for the scheduled batch", required = true, example = "MonthlyUserBatch")
        private String jobName;

        @Schema(name = "schedule_cron", description = "Cron or schedule descriptor", required = true, example = "0 0 1 * *")
        private String scheduleCron;

        @Schema(name = "api_endpoint", description = "API endpoint base URL", required = true, example = "https://fakerestapi.azurewebsites.net")
        private String apiEndpoint;

        @Schema(name = "admin_emails", description = "List of admin recipient emails", required = true, example = "[\"admin@example.com\"]")
        private List<String> adminEmails;

        @Schema(name = "metadata", description = "Optional metadata object")
        private java.util.Map<String, Object> metadata;
    }

    @Data
    @Schema(name = "UpdateJobRequest", description = "Request payload to update a BatchJob")
    public static class UpdateJobRequest {
        @Schema(name = "job_name", description = "Human name for the scheduled batch", example = "MonthlyUserBatch")
        private String jobName;

        @Schema(name = "schedule_cron", description = "Cron or schedule descriptor", example = "0 0 1 * *")
        private String scheduleCron;

        @Schema(name = "api_endpoint", description = "API endpoint base URL", example = "https://fakerestapi.azurewebsites.net")
        private String apiEndpoint;

        @Schema(name = "admin_emails", description = "List of admin recipient emails", example = "[\"admin@example.com\"]")
        private List<String> adminEmails;

        @Schema(name = "last_run_timestamp", description = "Last run timestamp")
        private String lastRunTimestamp;

        @Schema(name = "status", description = "Job status", example = "PENDING")
        private String status;

        @Schema(name = "created_at", description = "Creation timestamp")
        private String createdAt;

        @Schema(name = "metadata", description = "Optional metadata object")
        private java.util.Map<String, Object> metadata;
    }

    @Data
    @Schema(name = "GetJobResponse", description = "Response payload for BatchJob retrieval")
    public static class GetJobResponse {
        @Schema(name = "technicalId", description = "Technical ID of the entity")
        private String technicalId;

        @Schema(name = "job_name", description = "Human name for the scheduled batch")
        private String jobName;

        @Schema(name = "status", description = "Job status")
        private String status;

        @Schema(name = "last_run_timestamp", description = "Last run timestamp")
        private String lastRunTimestamp;

        @Schema(name = "created_at", description = "Creation timestamp")
        private String createdAt;

        @Schema(name = "api_endpoint", description = "API endpoint base URL")
        private String apiEndpoint;

        @Schema(name = "schedule_cron", description = "Cron or schedule descriptor")
        private String scheduleCron;

        @Schema(name = "admin_emails", description = "List of admin recipient emails")
        private List<String> adminEmails;

        @Schema(name = "metadata", description = "Optional metadata object")
        private java.util.Map<String, Object> metadata;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technical id of an entity")
    public static class TechnicalIdResponse {
        @Schema(name = "technicalId", description = "Technical ID of the entity")
        private String technicalId;
    }
}