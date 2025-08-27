package com.java_template.application.controller.report.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
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
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/report/v1")
@Tag(name = "Report", description = "Report entity proxy endpoints (version 1)")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get a report by technicalId", description = "Retrieve a stored Report by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/reports/{technicalId}")
    public ResponseEntity<?> getReportById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    uuid
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            ReportResponse resp = objectMapper.convertValue(node, ReportResponse.class);
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in getReportById: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getReportById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting report", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getReportById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all reports", description = "Retrieve all stored Reports")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/reports")
    public ResponseEntity<?> getAllReports() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION)
            );
            ArrayNode arrayNode = itemsFuture.get();
            List<ReportResponse> list = new ArrayList<>();
            if (arrayNode != null) {
                for (JsonNode node : arrayNode) {
                    ReportResponse resp = objectMapper.convertValue(node, ReportResponse.class);
                    // technicalId may be present in the stored JSON under some key; keep null if not present
                    if (node.has("technicalId")) {
                        resp.setTechnicalId(node.get("technicalId").asText());
                    }
                    list.add(resp);
                }
            }
            return ResponseEntity.ok(list);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in getAllReports", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getAllReports", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getAllReports", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search reports by condition", description = "Retrieve Reports matching a simple search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/reports/search")
    public ResponseEntity<?> searchReports(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest conditionRequest) {
        try {
            if (conditionRequest == null) {
                throw new IllegalArgumentException("Condition request must not be null");
            }
            String condition = objectMapper.writeValueAsString(conditionRequest);
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arrayNode = filteredItemsFuture.get();
            List<ReportResponse> list = new ArrayList<>();
            if (arrayNode != null) {
                for (JsonNode node : arrayNode) {
                    ReportResponse resp = objectMapper.convertValue(node, ReportResponse.class);
                    if (node.has("technicalId")) {
                        resp.setTechnicalId(node.get("technicalId").asText());
                    }
                    list.add(resp);
                }
            }
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in searchReports", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in searchReports", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in searchReports", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create a Report", description = "Persist a new Report. Returns the technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/reports")
    public ResponseEntity<?> createReport(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Report to create", required = true,
                    content = @Content(schema = @Schema(implementation = ReportRequest.class)))
            @RequestBody ReportRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            Report report = toEntity(request);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    report
            );
            UUID technicalId = idFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createReport request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createReport", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in createReport", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in createReport", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple Reports", description = "Persist multiple Reports. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/reports/bulk")
    public ResponseEntity<?> createReportsBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of reports to create", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportRequest.class))))
            @RequestBody List<ReportRequest> requests) {
        try {
            if (requests == null) {
                throw new IllegalArgumentException("Request body is required");
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
            List<CreateResponse> respList = new ArrayList<>();
            if (ids != null) {
                for (UUID id : ids) {
                    CreateResponse cr = new CreateResponse();
                    cr.setTechnicalId(id.toString());
                    respList.add(cr);
                }
            }
            return ResponseEntity.ok(respList);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createReportsBulk request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createReportsBulk", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in createReportsBulk", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in createReportsBulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update a report", description = "Update an existing Report by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/reports/{technicalId}")
    public ResponseEntity<?> updateReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Report update payload", required = true,
                    content = @Content(schema = @Schema(implementation = ReportRequest.class)))
            @RequestBody ReportRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            UUID uuid = UUID.fromString(technicalId);
            Report report = toEntity(request);
            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    uuid,
                    report
            );
            UUID result = updatedId.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(result.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid updateReport request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateReport", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in updateReport", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in updateReport", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete a report", description = "Delete a Report by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/reports/{technicalId}")
    public ResponseEntity<?> deleteReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    uuid
            );
            UUID result = deletedId.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(result.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid deleteReport request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteReport", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in deleteReport", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in deleteReport", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Helper to convert request DTO to entity
    private Report toEntity(ReportRequest req) {
        Report r = new Report();
        r.setFormat(req.getFormat());
        r.setGeneratedAt(req.getGeneratedAt());
        r.setPeriodEnd(req.getPeriodEnd());
        r.setPeriodStart(req.getPeriodStart());
        r.setReportId(req.getReportId());
        r.setSentAt(req.getSentAt());
        r.setStatus(req.getStatus());
        r.setTitleInsights(req.getTitleInsights());
        r.setTotalBooks(req.getTotalBooks());
        r.setTotalPageCount(req.getTotalPageCount());
        if (req.getPopularTitles() != null) {
            List<Report.BookSummary> summaries = new ArrayList<>();
            for (ReportRequest.BookSummaryRequest br : req.getPopularTitles()) {
                Report.BookSummary bs = new Report.BookSummary();
                bs.setDescription(br.getDescription());
                bs.setExcerpt(br.getExcerpt());
                bs.setPageCount(br.getPageCount());
                bs.setPublishDate(br.getPublishDate());
                bs.setTitle(br.getTitle());
                summaries.add(bs);
            }
            r.setPopularTitles(summaries);
        }
        return r;
    }

    // ======= DTOs ========

    @Data
    @Schema(name = "ReportRequest", description = "Payload to create or update a Report")
    public static class ReportRequest {
        @Schema(description = "Business report id")
        private String reportId;
        @Schema(description = "Start of period (ISO date)")
        private String periodStart;
        @Schema(description = "End of period (ISO date)")
        private String periodEnd;
        @Schema(description = "Generation timestamp (ISO)")
        private String generatedAt;
        @Schema(description = "Total number of books")
        private Integer totalBooks;
        @Schema(description = "Total page count")
        private Integer totalPageCount;
        @Schema(description = "Textual insights about titles")
        private String titleInsights;
        @Schema(description = "Popular titles summary")
        private List<BookSummaryRequest> popularTitles;
        @Schema(description = "Format (inline or attachment)")
        private String format;
        @Schema(description = "Status of the report")
        private String status;
        @Schema(description = "Sent timestamp (ISO)")
        private String sentAt;

        @Data
        @Schema(name = "BookSummaryRequest", description = "Summary of a popular title")
        public static class BookSummaryRequest {
            @Schema(description = "Title")
            private String title;
            @Schema(description = "Description")
            private String description;
            @Schema(description = "Excerpt")
            private String excerpt;
            @Schema(description = "Page count")
            private Integer pageCount;
            @Schema(description = "Publish date (ISO date)")
            private String publishDate;
        }
    }

    @Data
    @Schema(name = "ReportResponse", description = "Report returned from the API")
    public static class ReportResponse {
        @Schema(description = "Technical identifier")
        private String technicalId;
        @Schema(description = "Business report id")
        private String reportId;
        @Schema(description = "Start of period (ISO date)")
        private String periodStart;
        @Schema(description = "End of period (ISO date)")
        private String periodEnd;
        @Schema(description = "Generation timestamp (ISO)")
        private String generatedAt;
        @Schema(description = "Total number of books")
        private Integer totalBooks;
        @Schema(description = "Total page count")
        private Integer totalPageCount;
        @Schema(description = "Textual insights about titles")
        private String titleInsights;
        @Schema(description = "Popular titles summary")
        private List<BookSummaryResponse> popularTitles;
        @Schema(description = "Format (inline or attachment)")
        private String format;
        @Schema(description = "Status of the report")
        private String status;
        @Schema(description = "Sent timestamp (ISO)")
        private String sentAt;

        @Data
        @Schema(name = "BookSummaryResponse", description = "Summary of a popular title")
        public static class BookSummaryResponse {
            @Schema(description = "Title")
            private String title;
            @Schema(description = "Description")
            private String description;
            @Schema(description = "Excerpt")
            private String excerpt;
            @Schema(description = "Page count")
            private Integer pageCount;
            @Schema(description = "Publish date (ISO date)")
            private String publishDate;
        }
    }

    @Data
    @Schema(name = "CreateResponse", description = "Response containing technicalId")
    public static class CreateResponse {
        @Schema(description = "Technical identifier of the persisted entity")
        private String technicalId;
    }
}