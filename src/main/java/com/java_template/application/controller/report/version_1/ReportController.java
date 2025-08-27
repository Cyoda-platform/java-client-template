package com.java_template.application.controller.report.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/report/v1")
@Tag(name = "Report", description = "Report entity proxy controller (version 1)")
@RequiredArgsConstructor
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    /* ------------------------
       Create single report
       ------------------------ */
    @Operation(summary = "Create Report", description = "Persist a Report entity (proxy to EntityService). Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/reports")
    public ResponseEntity<TechnicalIdResponse> createReport(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Report payload", required = true)
            @RequestBody ReportRequest request
    ) {
        try {
            Report report = mapper.convertValue(request, Report.class);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    report
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for createReport", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in createReport", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating report", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error in createReport", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /* ------------------------
       Create multiple reports
       ------------------------ */
    @Operation(summary = "Create multiple Reports", description = "Persist multiple Report entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/reports/batch")
    public ResponseEntity<TechnicalIdsResponse> createReportsBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Report payloads", required = true)
            @RequestBody List<ReportRequest> requests
    ) {
        try {
            List<Report> reports = new ArrayList<>();
            for (ReportRequest r : requests) {
                reports.add(mapper.convertValue(r, Report.class));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    reports
            );
            List<UUID> ids = idsFuture.get();
            List<String> strIds = new ArrayList<>();
            for (UUID u : ids) strIds.add(u.toString());
            return ResponseEntity.ok(new TechnicalIdsResponse(strIds));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for createReportsBatch", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in createReportsBatch", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating reports batch", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error in createReportsBatch", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /* ------------------------
       Get single report by technicalId
       ------------------------ */
    @Operation(summary = "Get Report by technicalId", description = "Retrieve a Report entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/reports/{technicalId}")
    public ResponseEntity<ReportResponse> getReportById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            ReportResponse resp = mapper.treeToValue(node, ReportResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid argument in getReportById", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in getReportById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getReportById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error in getReportById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /* ------------------------
       Get all reports
       ------------------------ */
    @Operation(summary = "Get all Reports", description = "Retrieve all Report entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/reports")
    public ResponseEntity<List<ReportResponse>> getAllReports() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<ReportResponse> list = new ArrayList<>();
            if (array != null) {
                for (JsonNode node : array) {
                    ReportResponse rr = mapper.treeToValue(node, ReportResponse.class);
                    list.add(rr);
                }
            }
            return ResponseEntity.ok(list);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in getAllReports", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getAllReports", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error in getAllReports", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /* ------------------------
       Filtered retrieval by SearchConditionRequest (in-memory)
       ------------------------ */
    @Operation(summary = "Search Reports by condition", description = "Retrieve Report entities by a search condition (in-memory filter).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/reports/search")
    public ResponseEntity<List<ReportResponse>> searchReportsByCondition(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition payload", required = true)
            @RequestBody SearchConditionRequest condition
    ) {
        try {
            CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = filteredFuture.get();
            List<ReportResponse> list = new ArrayList<>();
            if (array != null) {
                for (JsonNode node : array) {
                    ReportResponse rr = mapper.treeToValue(node, ReportResponse.class);
                    list.add(rr);
                }
            }
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request in searchReportsByCondition", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in searchReportsByCondition", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in searchReportsByCondition", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error in searchReportsByCondition", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /* ------------------------
       Update report by technicalId
       ------------------------ */
    @Operation(summary = "Update Report", description = "Update a Report entity identified by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(value = "/reports/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> updateReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Report payload", required = true)
            @RequestBody ReportRequest request
    ) {
        try {
            Report report = mapper.convertValue(request, Report.class);
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    report
            );
            UUID updatedId = updatedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid argument in updateReport", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in updateReport", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in updateReport", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error in updateReport", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /* ------------------------
       Delete report by technicalId
       ------------------------ */
    @Operation(summary = "Delete Report", description = "Delete a Report entity identified by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(value = "/reports/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> deleteReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID deletedId = deletedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deletedId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid argument in deleteReport", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in deleteReport", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in deleteReport", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error in deleteReport", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /* ==============================
       Static DTO classes for requests/responses
       ============================== */

    @Data
    @Schema(name = "ReportRequest", description = "Payload to create or update a Report")
    public static class ReportRequest {
        @Schema(description = "Business report identifier")
        private String reportId;
        @Schema(description = "Reference to originating job")
        private String jobReference;
        @Schema(description = "ISO timestamp when generated")
        private String generatedAt;
        @Schema(description = "ISO timestamp when exported")
        private String exportedAt;
        @Schema(description = "Metrics object (arbitrary structure)")
        private Object metrics;
        @Schema(description = "Rows (list of booking summaries)")
        private List<Object> rows;
        @Schema(description = "Scope / filter criteria")
        private Object scope;
        @Schema(description = "Visualizations (chart/table data)")
        private Object visualizations;
    }

    @Data
    @Schema(name = "ReportResponse", description = "Report retrieval response")
    public static class ReportResponse {
        @Schema(description = "Business report identifier")
        private String reportId;
        @Schema(description = "Reference to originating job")
        private String jobReference;
        @Schema(description = "ISO timestamp when generated")
        private String generatedAt;
        @Schema(description = "ISO timestamp when exported")
        private String exportedAt;
        @Schema(description = "Metrics object (arbitrary structure)")
        private Object metrics;
        @Schema(description = "Rows (list of booking summaries)")
        private List<Object> rows;
        @Schema(description = "Scope / filter criteria")
        private Object scope;
        @Schema(description = "Visualizations (chart/table data)")
        private Object visualizations;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing a single technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical identifier for the persisted entity")
        private String technicalId;

        public TechnicalIdResponse() {
        }

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing multiple technicalIds")
    public static class TechnicalIdsResponse {
        @ArraySchema(schema = @Schema(description = "List of technical identifiers", implementation = String.class))
        private List<String> technicalIds;

        public TechnicalIdsResponse() {
        }

        public TechnicalIdsResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }
}