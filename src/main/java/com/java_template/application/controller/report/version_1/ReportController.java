package com.java_template.application.controller.report.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.service.EntityService;
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
@RequestMapping("/api/report/v1/reports")
@Tag(name = "Report", description = "Report entity proxy endpoints (version 1)")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Report", description = "Create a Report entity (returns technicalId). This controller is a proxy to the EntityService; business logic is implemented in workflows.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> createReport(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Report payload", required = true,
                    content = @Content(schema = @Schema(implementation = ReportRequest.class)))
            @RequestBody ReportRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is null");
            }

            Report entity = toEntity(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    entity
            );

            UUID technicalId = idFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to create report: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating report", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error while creating report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Bulk create Reports", description = "Create multiple Report entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createReportsBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Report payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportRequest.class))))
            @RequestBody List<ReportRequest> requests
    ) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body is null or empty");
            }

            List<Report> entities = new ArrayList<>();
            for (ReportRequest r : requests) {
                entities.add(toEntity(r));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    entities
            );

            List<UUID> ids = idsFuture.get();
            List<CreateResponse> responses = new ArrayList<>();
            for (UUID id : ids) {
                CreateResponse cr = new CreateResponse();
                cr.setTechnicalId(id.toString());
                responses.add(cr);
            }
            return ResponseEntity.ok(responses);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid bulk create request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while bulk creating reports", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error while bulk creating reports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Report by technicalId", description = "Retrieve a Report by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getReportById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Report not found");
            }
            ReportResponse resp = mapper.convertValue(node, ReportResponse.class);
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for getReportById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving report", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error while retrieving report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Reports", description = "Retrieve all Reports.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> getAllReports() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION)
            );

            ArrayNode arr = itemsFuture.get();
            List<ReportResponse> responses = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    ReportResponse r = mapper.convertValue(arr.get(i), ReportResponse.class);
                    // try to set technicalId if present in node
                    if (arr.get(i).has("id")) {
                        r.setTechnicalId(arr.get(i).get("id").asText());
                    }
                    responses.add(r);
                }
            }
            return ResponseEntity.ok(responses);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving all reports", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error while retrieving all reports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Reports by condition", description = "Retrieve Reports matching a simple search condition. Uses in-memory filtering.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchReportsByCondition(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition
    ) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition is null");
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode arr = filteredItemsFuture.get();
            List<ReportResponse> responses = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    ReportResponse r = mapper.convertValue(arr.get(i), ReportResponse.class);
                    if (arr.get(i).has("id")) {
                        r.setTechnicalId(arr.get(i).get("id").asText());
                    }
                    responses.add(r);
                }
            }
            return ResponseEntity.ok(responses);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search condition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching reports", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error while searching reports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Report", description = "Update a Report entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Report payload", required = true,
                    content = @Content(schema = @Schema(implementation = ReportRequest.class)))
            @RequestBody ReportRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is null");
            }
            UUID id = UUID.fromString(technicalId);
            Report entity = toEntity(request);

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    id,
                    entity
            );

            UUID updatedId = updatedIdFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid update request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating report", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error while updating report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Report", description = "Delete a Report entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    id
            );

            UUID deletedId = deletedIdFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for delete: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting report", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error while deleting report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Simple mapper from DTO to entity.Report
    private Report toEntity(ReportRequest req) {
        Report r = new Report();
        r.setId(req.getTechnicalId());
        r.setJobReference(req.getJobReference());
        r.setGeneratedAt(req.getGeneratedAt());
        r.setSummary(req.getSummary());
        r.setStorageLocation(req.getStorageLocation());
        // metrics
        if (req.getMetrics() != null) {
            Report.Metrics m = new Report.Metrics();
            m.setAveragePrice(req.getMetrics().getAveragePrice());
            m.setTotalItems(req.getMetrics().getTotalItems());
            m.setTotalQuantity(req.getMetrics().getTotalQuantity());
            m.setTotalValue(req.getMetrics().getTotalValue());
            r.setMetrics(m);
        }
        // visuals
        if (req.getVisuals() != null) {
            Report.Visuals v = new Report.Visuals();
            v.setChartType(req.getVisuals().getChartType());
            v.setReference(req.getVisuals().getReference());
            r.setVisuals(v);
        }
        // rows
        if (req.getRows() != null) {
            List<Report.Row> rows = new ArrayList<>();
            for (ReportRow rr : req.getRows()) {
                Report.Row row = new Report.Row();
                row.setId(rr.getId());
                row.setName(rr.getName());
                row.setPrice(rr.getPrice());
                row.setQuantity(rr.getQuantity());
                row.setValue(rr.getValue());
                rows.add(row);
            }
            r.setRows(rows);
        }
        return r;
    }

    // DTOs

    @Data
    @Schema(name = "CreateResponse", description = "Response that contains the technicalId of the created/updated/deleted entity")
    public static class CreateResponse {
        @Schema(description = "Technical ID of the entity")
        private String technicalId;
    }

    @Data
    @Schema(name = "ReportRequest", description = "Request payload for creating or updating a Report")
    public static class ReportRequest {
        @Schema(description = "Technical ID (UUID string). For creates this may be null or client provided.")
        private String technicalId;

        @Schema(description = "Reference to the ReportJob technicalId")
        private String jobReference;

        @Schema(description = "ISO-8601 timestamp when report was generated")
        private String generatedAt;

        @Schema(description = "Metrics related to the report")
        private ReportMetrics metrics;

        @Schema(description = "Tabular rows used in the visual")
        private List<ReportRow> rows;

        @Schema(description = "Storage location reference (optional)")
        private String storageLocation;

        @Schema(description = "Textual summary/highlights")
        private String summary;

        @Schema(description = "Visuals metadata or references")
        private ReportVisuals visuals;
    }

    @Data
    @Schema(name = "ReportResponse", description = "Response payload when retrieving a Report")
    public static class ReportResponse {
        @Schema(description = "Technical ID of the report")
        private String technicalId;

        @Schema(description = "Reference to the ReportJob technicalId")
        private String jobReference;

        @Schema(description = "ISO-8601 timestamp when report was generated")
        private String generatedAt;

        @Schema(description = "Metrics related to the report")
        private ReportMetrics metrics;

        @Schema(description = "Tabular rows used in the visual")
        private List<ReportRow> rows;

        @Schema(description = "Storage location reference (optional)")
        private String storageLocation;

        @Schema(description = "Textual summary/highlights")
        private String summary;

        @Schema(description = "Visuals metadata or references")
        private ReportVisuals visuals;
    }

    @Data
    @Schema(name = "ReportMetrics", description = "Metrics object for a report")
    public static class ReportMetrics {
        @Schema(description = "Average price")
        private Double averagePrice;

        @Schema(description = "Total distinct items")
        private Integer totalItems;

        @Schema(description = "Total quantity")
        private Integer totalQuantity;

        @Schema(description = "Total monetary value")
        private Double totalValue;
    }

    @Data
    @Schema(name = "ReportRow", description = "A single row in the report table")
    public static class ReportRow {
        @Schema(description = "Row id")
        private String id;

        @Schema(description = "Item name")
        private String name;

        @Schema(description = "Price")
        private Double price;

        @Schema(description = "Quantity")
        private Integer quantity;

        @Schema(description = "Computed value (price * quantity)")
        private Double value;
    }

    @Data
    @Schema(name = "ReportVisuals", description = "Visuals metadata for the report")
    public static class ReportVisuals {
        @Schema(description = "Chart type")
        private String chartType;

        @Schema(description = "Reference to chart or visualization artifact")
        private String reference;
    }
}