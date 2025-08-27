package com.java_template.application.controller.weeklyjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.weeklyjob.version_1.WeeklyJob;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs")
@Tag(name = "WeeklyJob Controller", description = "CRUD proxy controller for WeeklyJob entity (version 1)")
public class WeeklyJobController {

    private static final Logger logger = LoggerFactory.getLogger(WeeklyJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeeklyJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create WeeklyJob", description = "Create a WeeklyJob entity. Returns the technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createWeeklyJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "WeeklyJob creation payload",
                    content = @Content(schema = @Schema(implementation = CreateWeeklyJobRequest.class)))
            @RequestBody CreateWeeklyJobRequest request
    ) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            WeeklyJob job = new WeeklyJob();
            job.setName(request.getName());
            job.setRecurrenceDay(request.getRecurrenceDay());
            job.setRunTime(request.getRunTime());
            job.setTimezone(request.getTimezone());
            job.setApiEndpoint(request.getApiEndpoint());
            job.setRecipients(request.getRecipients());
            job.setFailurePolicy(request.getFailurePolicy());
            // Default initial status required by entity validation; workflows will handle state transitions.
            job.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    WeeklyJob.ENTITY_NAME,
                    String.valueOf(WeeklyJob.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid createWeeklyJob request: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createWeeklyJob", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during createWeeklyJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Bulk create WeeklyJobs", description = "Create multiple WeeklyJob entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = BulkCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> bulkCreateWeeklyJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Bulk WeeklyJob creation payload",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateWeeklyJobRequest.class))))
            @RequestBody BulkCreateRequest request
    ) {
        try {
            if (request == null || request.getJobs() == null || request.getJobs().isEmpty()) {
                throw new IllegalArgumentException("jobs payload is required and must not be empty");
            }

            List<WeeklyJob> jobs = new ArrayList<>();
            for (CreateWeeklyJobRequest r : request.getJobs()) {
                WeeklyJob job = new WeeklyJob();
                job.setName(r.getName());
                job.setRecurrenceDay(r.getRecurrenceDay());
                job.setRunTime(r.getRunTime());
                job.setTimezone(r.getTimezone());
                job.setApiEndpoint(r.getApiEndpoint());
                job.setRecipients(r.getRecipients());
                job.setFailurePolicy(r.getFailurePolicy());
                job.setStatus("PENDING");
                jobs.add(job);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    WeeklyJob.ENTITY_NAME,
                    String.valueOf(WeeklyJob.ENTITY_VERSION),
                    jobs
            );

            List<UUID> ids = idsFuture.get();
            BulkCreateResponse resp = new BulkCreateResponse();
            List<String> technicalIds = new ArrayList<>();
            for (UUID id : ids) technicalIds.add(id.toString());
            resp.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid bulkCreateWeeklyJobs request: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during bulkCreateWeeklyJobs", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during bulkCreateWeeklyJobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get WeeklyJob by technicalId", description = "Retrieve a WeeklyJob by its technical UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = WeeklyJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getWeeklyJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    WeeklyJob.ENTITY_NAME,
                    String.valueOf(WeeklyJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode obj = itemFuture.get();
            WeeklyJobResponse resp = objectMapper.treeToValue(obj, WeeklyJobResponse.class);
            // ensure technicalId is present in response
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid getWeeklyJobById request: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getWeeklyJobById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during getWeeklyJobById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all WeeklyJobs", description = "Retrieve all WeeklyJob entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = WeeklyJobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllWeeklyJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    WeeklyJob.ENTITY_NAME,
                    String.valueOf(WeeklyJob.ENTITY_VERSION)
            );

            ArrayNode arr = itemsFuture.get();
            List<WeeklyJobResponse> list = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    ObjectNode node = (ObjectNode) arr.get(i);
                    WeeklyJobResponse resp = objectMapper.treeToValue(node, WeeklyJobResponse.class);
                    // if id present in node, set technicalId
                    if (node.has("id") && !node.get("id").isNull()) {
                        resp.setTechnicalId(node.get("id").asText());
                    }
                    list.add(resp);
                }
            }
            return ResponseEntity.ok(list);

        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getAllWeeklyJobs", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during getAllWeeklyJobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search WeeklyJobs by condition", description = "Retrieve WeeklyJob entities that match a simple search condition.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = WeeklyJobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchWeeklyJobsByCondition(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request",
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition
    ) {
        try {
            if (condition == null) throw new IllegalArgumentException("condition is required");

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    WeeklyJob.ENTITY_NAME,
                    String.valueOf(WeeklyJob.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode arr = filteredItemsFuture.get();
            List<WeeklyJobResponse> list = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    ObjectNode node = (ObjectNode) arr.get(i);
                    WeeklyJobResponse resp = objectMapper.treeToValue(node, WeeklyJobResponse.class);
                    if (node.has("id") && !node.get("id").isNull()) {
                        resp.setTechnicalId(node.get("id").asText());
                    }
                    list.add(resp);
                }
            }
            return ResponseEntity.ok(list);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid searchWeeklyJobsByCondition request: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during searchWeeklyJobsByCondition", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during searchWeeklyJobsByCondition", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update WeeklyJob", description = "Update a WeeklyJob entity by technicalId. Returns the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateWeeklyJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "WeeklyJob update payload",
                    content = @Content(schema = @Schema(implementation = CreateWeeklyJobRequest.class)))
            @RequestBody CreateWeeklyJobRequest request
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            WeeklyJob job = new WeeklyJob();
            job.setName(request.getName());
            job.setRecurrenceDay(request.getRecurrenceDay());
            job.setRunTime(request.getRunTime());
            job.setTimezone(request.getTimezone());
            job.setApiEndpoint(request.getApiEndpoint());
            job.setRecipients(request.getRecipients());
            job.setFailurePolicy(request.getFailurePolicy());
            // For update, status should be provided by workflow or caller; we do not change it here.
            // If status absent, keep as-is by not setting it. But WeeklyJob.isValid requires status non-null;
            // the entityService/update operation expects a full entity. If client wants to update status they should send it.
            // To keep controller dumb, we will NOT set status here. Caller should provide a full representation if needed.

            CompletableFuture<java.util.UUID> updatedFuture = entityService.updateItem(
                    WeeklyJob.ENTITY_NAME,
                    String.valueOf(WeeklyJob.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    job
            );

            UUID id = updatedFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid updateWeeklyJob request: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during updateWeeklyJob", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during updateWeeklyJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete WeeklyJob", description = "Delete a WeeklyJob by technicalId. Returns the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteWeeklyJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<java.util.UUID> deletedFuture = entityService.deleteItem(
                    WeeklyJob.ENTITY_NAME,
                    String.valueOf(WeeklyJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            UUID id = deletedFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid deleteWeeklyJob request: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during deleteWeeklyJob", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during deleteWeeklyJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // --- Static DTO classes ---

    @Data
    @Schema(name = "CreateWeeklyJobRequest", description = "Request payload to create a WeeklyJob")
    public static class CreateWeeklyJobRequest {
        @Schema(description = "Human name for the scheduled job", example = "Weekly Book Summary")
        private String name;

        @Schema(description = "Weekday for run, e.g., Wednesday", example = "Wednesday")
        private String recurrenceDay;

        @Schema(description = "Time of day in HH:MM format", example = "09:00")
        private String runTime;

        @Schema(description = "Timezone for scheduling", example = "UTC")
        private String timezone;

        @Schema(description = "Source API base URL", example = "https://fakerestapi.azurewebsites.net")
        private String apiEndpoint;

        @Schema(description = "Emails to send report to")
        private List<String> recipients;

        @Schema(description = "Retry policy description", example = "retry 3 times then alert")
        private String failurePolicy;

        // status intentionally omitted for create request; controller will set initial status.
    }

    @Data
    @Schema(name = "CreateResponse", description = "Response containing technicalId")
    public static class CreateResponse {
        @Schema(description = "Technical ID of the created/affected entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "BulkCreateRequest", description = "Bulk create request containing a list of WeeklyJob creation payloads")
    public static class BulkCreateRequest {
        @Schema(description = "List of jobs to create")
        private List<CreateWeeklyJobRequest> jobs;
    }

    @Data
    @Schema(name = "BulkCreateResponse", description = "Bulk create response containing technicalIds")
    public static class BulkCreateResponse {
        @Schema(description = "List of technical IDs of created entities")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "WeeklyJobResponse", description = "WeeklyJob response payload")
    public static class WeeklyJobResponse {
        @Schema(description = "Technical ID of the entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Human name for the scheduled job")
        private String name;

        @Schema(description = "Weekday for run, e.g., Wednesday")
        private String recurrenceDay;

        @Schema(description = "Time of day in HH:MM format")
        private String runTime;

        @Schema(description = "Timezone for scheduling")
        private String timezone;

        @Schema(description = "Source API base URL")
        private String apiEndpoint;

        @Schema(description = "Emails to send report to")
        private List<String> recipients;

        @Schema(description = "Retry policy description")
        private String failurePolicy;

        @Schema(description = "Timestamp of last run")
        private String lastRunAt;

        @Schema(description = "Timestamp of next run")
        private String nextRunAt;

        @Schema(description = "Status (PENDING/RUNNING/COMPLETED/FAILED)")
        private String status;
    }
}