package com.java_template.application.controller.reportjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/report-jobs")
@Tag(name = "ReportJob Controller", description = "Proxy controller for ReportJob entity operations")
@RequiredArgsConstructor
public class ReportJobController {
    private static final Logger logger = LoggerFactory.getLogger(ReportJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Create ReportJob", description = "Create a ReportJob entity (triggers workflows). Returns technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createReportJob(@RequestBody CreateReportJobRequest request) {
        try {
            ReportJob reportJob = toReportJob(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    reportJob
            );
            UUID technicalId = idFuture.get();
            CreateResponse resp = new CreateResponse(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createReportJob", iae);
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(Map.of("error", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(Map.of("error", cause.getMessage()));
            } else {
                logger.error("ExecutionException on createReportJob", ee);
                return ResponseEntity.status(500).body(Map.of("error", ee.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error on createReportJob", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Create multiple ReportJobs", description = "Create multiple ReportJob entities in bulk. Returns list of technicalIds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BulkCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createReportJobsBulk(@RequestBody List<CreateReportJobRequest> requests) {
        try {
            List<ReportJob> entities = requests.stream().map(this::toReportJob).collect(Collectors.toList());
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<String> technicalIds = ids.stream().map(UUID::toString).collect(Collectors.toList());
            return ResponseEntity.ok(new BulkCreateResponse(technicalIds));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createReportJobsBulk", iae);
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(Map.of("error", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(Map.of("error", cause.getMessage()));
            } else {
                logger.error("ExecutionException on createReportJobsBulk", ee);
                return ResponseEntity.status(500).body(Map.of("error", ee.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error on createReportJobsBulk", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Get ReportJob by technicalId", description = "Retrieve a ReportJob by its technical identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetReportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getReportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    tid
            );
            ObjectNode node = itemFuture.get();
            GetReportJobResponse resp = objectMapper.convertValue(node, GetReportJobResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getReportJob", iae);
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(Map.of("error", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(Map.of("error", cause.getMessage()));
            } else {
                logger.error("ExecutionException on getReportJob", ee);
                return ResponseEntity.status(500).body(Map.of("error", ee.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error on getReportJob", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Get all ReportJobs", description = "Retrieve all ReportJob entities.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Object.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllReportJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(Map.of("error", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(Map.of("error", cause.getMessage()));
            } else {
                logger.error("ExecutionException on getAllReportJobs", ee);
                return ResponseEntity.status(500).body(Map.of("error", ee.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error on getAllReportJobs", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Search ReportJobs by condition", description = "Retrieve ReportJob entities by a simple search condition.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Object.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchReportJobs(@RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search condition for searchReportJobs", iae);
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(Map.of("error", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(Map.of("error", cause.getMessage()));
            } else {
                logger.error("ExecutionException on searchReportJobs", ee);
                return ResponseEntity.status(500).body(Map.of("error", ee.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error on searchReportJobs", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Update ReportJob", description = "Update a ReportJob entity by technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateReportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @RequestBody UpdateReportJobRequest request
    ) {
        try {
            UUID tid = UUID.fromString(technicalId);
            ReportJob reportJob = toReportJob(request.toCreateRequest());
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    tid,
                    reportJob
            );
            UUID updatedId = updatedFuture.get();
            return ResponseEntity.ok(new UpdateResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateReportJob", iae);
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(Map.of("error", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(Map.of("error", cause.getMessage()));
            } else {
                logger.error("ExecutionException on updateReportJob", ee);
                return ResponseEntity.status(500).body(Map.of("error", ee.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error on updateReportJob", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Delete ReportJob", description = "Delete a ReportJob entity by technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteReportJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    tid
            );
            UUID deletedId = deletedFuture.get();
            return ResponseEntity.ok(new DeleteResponse(deletedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for deleteReportJob", iae);
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(Map.of("error", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(Map.of("error", cause.getMessage()));
            } else {
                logger.error("ExecutionException on deleteReportJob", ee);
                return ResponseEntity.status(500).body(Map.of("error", ee.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error on deleteReportJob", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // --- Helpers and DTO mappings ---

    private ReportJob toReportJob(CreateReportJobRequest req) {
        if (req == null) throw new IllegalArgumentException("Request cannot be null");
        ReportJob r = new ReportJob();
        // Preserve provided values when present; fill minimal required fields otherwise.
        r.setJobId((req.getJobId() != null && !req.getJobId().isBlank()) ? req.getJobId() : UUID.randomUUID().toString());
        r.setRequestedAt((req.getRequestedAt() != null && !req.getRequestedAt().isBlank()) ? req.getRequestedAt() : java.time.Instant.now().toString());
        r.setRequestedBy(req.getRequestedBy());
        r.setSchedule(req.getSchedule());
        r.setVisualization(req.getVisualization());
        r.setStatus((req.getStatus() != null && !req.getStatus().isBlank()) ? req.getStatus() : "CREATED");
        // Map filterCriteria if present
        if (req.getFilterCriteria() != null) {
            CreateReportJobRequest.FilterCriteria fcReq = req.getFilterCriteria();
            ReportJob.FilterCriteria fc = new ReportJob.FilterCriteria();
            fc.setCustomerName(fcReq.getCustomerName());
            fc.setDepositStatus(fcReq.getDepositStatus());
            fc.setMaxPrice(fcReq.getMaxPrice());
            fc.setMinPrice(fcReq.getMinPrice());
            if (fcReq.getDateRange() != null) {
                CreateReportJobRequest.DateRange drReq = fcReq.getDateRange();
                ReportJob.DateRange dr = new ReportJob.DateRange();
                dr.setFrom(drReq.getFrom());
                dr.setTo(drReq.getTo());
                fc.setDateRange(dr);
            }
            r.setFilterCriteria(fc);
        }
        // resultReportId left as provided (likely null)
        r.setResultReportId(req.getResultReportId());
        return r;
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateReportJobRequest", description = "Payload to create a ReportJob")
    public static class CreateReportJobRequest {
        @Schema(description = "Optional business job id", example = "job-123456")
        private String jobId;

        @Schema(description = "ISO timestamp when requested (optional)", example = "2025-08-27T12:00:00Z")
        private String requestedAt;

        @Schema(description = "User who requested the job", example = "alice@example.com", required = true)
        private String requestedBy;

        @Schema(description = "Filter criteria for the job")
        private FilterCriteria filterCriteria;

        @Schema(description = "Visualization type (table/chart)")
        private String visualization;

        @Schema(description = "Optional schedule/cron")
        private String schedule;

        @Schema(description = "Optional status", example = "CREATED")
        private String status;

        @Schema(description = "Optional resultReportId if known")
        private String resultReportId;

        @Data
        @Schema(name = "FilterCriteria", description = "Filtering criteria for ReportJob")
        public static class FilterCriteria {
            @Schema(description = "Customer name to filter", example = "John Doe")
            private String customerName;

            @Schema(description = "Date range filter")
            private DateRange dateRange;

            @Schema(description = "Deposit status filter", example = "paid")
            private String depositStatus;

            @Schema(description = "Maximum price filter", example = "500.0")
            private Double maxPrice;

            @Schema(description = "Minimum price filter", example = "50.0")
            private Double minPrice;
        }

        @Data
        @Schema(name = "DateRange", description = "Date range used in filterCriteria")
        public static class DateRange {
            @Schema(description = "From date ISO", example = "2025-01-01")
            private String from;

            @Schema(description = "To date ISO", example = "2025-01-31")
            private String to;
        }
    }

    @Data
    @Schema(name = "CreateResponse", description = "Response containing the technical identifier")
    public static class CreateResponse {
        @Schema(description = "Technical identifier of the created entity")
        private String technicalId;

        public CreateResponse() {}

        public CreateResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "BulkCreateResponse", description = "Response containing multiple technical identifiers")
    public static class BulkCreateResponse {
        @Schema(description = "List of technical identifiers")
        private List<String> technicalIds;

        public BulkCreateResponse() {}

        public BulkCreateResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "GetReportJobResponse", description = "Response payload for ReportJob retrieval")
    public static class GetReportJobResponse {
        @Schema(description = "Business job id")
        private String jobId;

        @Schema(description = "Job status", example = "COMPLETED")
        private String status;

        @Schema(description = "User who requested the job")
        private String requestedBy;

        @Schema(description = "ISO timestamp when requested")
        private String requestedAt;

        @Schema(description = "Reference to generated report id if any")
        private String resultReportId;

        @Schema(description = "Filter criteria (raw object)")
        private Object filterCriteria;

        @Schema(description = "Visualization type")
        private String visualization;

        @Schema(description = "Schedule descriptor")
        private String schedule;
    }

    @Data
    @Schema(name = "UpdateReportJobRequest", description = "Payload to update a ReportJob")
    public static class UpdateReportJobRequest {
        @Schema(description = "Fields to update. This mirrors CreateReportJobRequest")
        private CreateReportJobRequest payload;

        public CreateReportJobRequest toCreateRequest() {
            return payload;
        }
    }

    @Data
    @Schema(name = "UpdateResponse", description = "Response returned after update")
    public static class UpdateResponse {
        @Schema(description = "Technical identifier of the updated entity")
        private String technicalId;

        public UpdateResponse() {}

        public UpdateResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "DeleteResponse", description = "Response returned after delete")
    public static class DeleteResponse {
        @Schema(description = "Technical identifier of the deleted entity")
        private String technicalId;

        public DeleteResponse() {}

        public DeleteResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}