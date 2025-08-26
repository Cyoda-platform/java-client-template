package com.java_template.application.controller.analysisreport.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.analysisreport.version_1.AnalysisReport;
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
@RequestMapping("/api/analysisreports/v1")
@Tag(name = "AnalysisReport", description = "API for AnalysisReport entity (version 1)")
public class AnalysisReportController {
    private static final Logger logger = LoggerFactory.getLogger(AnalysisReportController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalysisReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create AnalysisReport", description = "Create a new AnalysisReport entity. Returns technicalId (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createAnalysisReport(@RequestBody AnalysisReportRequest request) {
        try {
            // Map request DTO to entity
            AnalysisReport entity = mapToEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    AnalysisReport.ENTITY_NAME,
                    String.valueOf(AnalysisReport.ENTITY_VERSION),
                    entity
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for createAnalysisReport", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createAnalysisReport", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createAnalysisReport", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Bulk create AnalysisReports", description = "Create multiple AnalysisReport entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createAnalysisReportsBulk(@RequestBody List<AnalysisReportRequest> requests) {
        try {
            List<AnalysisReport> entities = new ArrayList<>();
            for (AnalysisReportRequest r : requests) {
                entities.add(mapToEntity(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    AnalysisReport.ENTITY_NAME,
                    String.valueOf(AnalysisReport.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<TechnicalIdResponse> resp = new ArrayList<>();
            for (UUID id : ids) resp.add(new TechnicalIdResponse(id.toString()));
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for createAnalysisReportsBulk", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createAnalysisReportsBulk", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createAnalysisReportsBulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get AnalysisReport by technicalId", description = "Retrieve an AnalysisReport by its technicalId (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AnalysisReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getAnalysisReportById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId
    ) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    AnalysisReport.ENTITY_NAME,
                    String.valueOf(AnalysisReport.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            AnalysisReportResponse response = objectMapper.convertValue(node, AnalysisReportResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId for getAnalysisReportById", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAnalysisReportById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getAnalysisReportById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List all AnalysisReports", description = "Retrieve all AnalysisReport entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AnalysisReportResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listAnalysisReports() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    AnalysisReport.ENTITY_NAME,
                    String.valueOf(AnalysisReport.ENTITY_VERSION)
            );
            ArrayNode arrayNode = itemsFuture.get();
            List<AnalysisReportResponse> resp = new ArrayList<>();
            for (int i = 0; i < arrayNode.size(); i++) {
                resp.add(objectMapper.convertValue(arrayNode.get(i), AnalysisReportResponse.class));
            }
            return ResponseEntity.ok(resp);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in listAnalysisReports", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in listAnalysisReports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search AnalysisReports by conditions", description = "Search AnalysisReports using simple field conditions.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AnalysisReportResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchAnalysisReports(@RequestBody SearchRequest request) {
        try {
            // Build single-group AND conditions from request.conditions
            List<Condition> conds = new ArrayList<>();
            if (request.getConditions() != null) {
                for (SearchRequest.ConditionDto c : request.getConditions()) {
                    String jsonPath = c.getFieldName().startsWith("$") ? c.getFieldName() : "$." + c.getFieldName();
                    conds.add(Condition.of(jsonPath, c.getOperator(), c.getValue()));
                }
            }
            SearchConditionRequest condition = SearchConditionRequest.group("AND", conds.toArray(new Condition[0]));
            CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                    AnalysisReport.ENTITY_NAME,
                    String.valueOf(AnalysisReport.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arrayNode = filteredFuture.get();
            List<AnalysisReportResponse> resp = new ArrayList<>();
            for (int i = 0; i < arrayNode.size(); i++) {
                resp.add(objectMapper.convertValue(arrayNode.get(i), AnalysisReportResponse.class));
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for searchAnalysisReports", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchAnalysisReports", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in searchAnalysisReports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update AnalysisReport", description = "Update an existing AnalysisReport by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateAnalysisReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId,
            @RequestBody AnalysisReportRequest request
    ) {
        try {
            AnalysisReport entity = mapToEntity(request);
            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    AnalysisReport.ENTITY_NAME,
                    String.valueOf(AnalysisReport.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    entity
            );
            UUID id = updatedId.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for updateAnalysisReport", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateAnalysisReport", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in updateAnalysisReport", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete AnalysisReport", description = "Delete an AnalysisReport by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteAnalysisReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId
    ) {
        try {
            CompletableFuture<java.util.UUID> deletedId = entityService.deleteItem(
                    AnalysisReport.ENTITY_NAME,
                    String.valueOf(AnalysisReport.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID id = deletedId.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId for deleteAnalysisReport", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteAnalysisReport", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in deleteAnalysisReport", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Utility mapping (no business logic)
    private AnalysisReport mapToEntity(AnalysisReportRequest req) {
        AnalysisReport e = new AnalysisReport();
        e.setReportId(req.getReportId());
        e.setJobId(req.getJobId());
        e.setPostId(req.getPostId());
        e.setRecipientEmail(req.getRecipientEmail());
        e.setGeneratedAt(req.getGeneratedAt());
        e.setSentAt(req.getSentAt());
        e.setStatus(req.getStatus());
        e.setSummary(req.getSummary());
        if (req.getMetrics() != null) {
            AnalysisReport.Metrics m = new AnalysisReport.Metrics();
            m.setCount(req.getMetrics().getCount());
            m.setAvgLengthWords(req.getMetrics().getAvgLengthWords());
            m.setSentimentSummary(req.getMetrics().getSentimentSummary());
            m.setTopWords(req.getMetrics().getTopWords());
            e.setMetrics(m);
        } else {
            e.setMetrics(null);
        }
        return e;
    }

    // DTOs

    @Data
    @Schema(name = "AnalysisReportRequest", description = "Request payload to create or update AnalysisReport")
    public static class AnalysisReportRequest {
        @JsonProperty("report_id")
        @Schema(description = "Unique report id", example = "report_abc")
        private String reportId;

        @JsonProperty("job_id")
        @Schema(description = "Reference to CommentAnalysisJob", example = "job_123456")
        private String jobId;

        @JsonProperty("post_id")
        @Schema(description = "Post identifier", example = "1")
        private Integer postId;

        @JsonProperty("generated_at")
        @Schema(description = "ISO timestamp when generated", example = "2025-08-26T12:00:20Z")
        private String generatedAt;

        @JsonProperty("summary")
        @Schema(description = "Human readable summary", example = "5 comments analyzed...")
        private String summary;

        @JsonProperty("metrics")
        @Schema(description = "Computed metrics")
        private MetricsDto metrics;

        @JsonProperty("recipient_email")
        @Schema(description = "Recipient email", example = "ops@example.com")
        private String recipientEmail;

        @JsonProperty("status")
        @Schema(description = "Status of report", example = "SENT")
        private String status;

        @JsonProperty("sent_at")
        @Schema(description = "ISO timestamp when sent", example = "2025-08-26T12:01:00Z")
        private String sentAt;
    }

    @Data
    @Schema(name = "AnalysisReportResponse", description = "Response payload for AnalysisReport")
    public static class AnalysisReportResponse {
        @JsonProperty("report_id")
        @Schema(description = "Unique report id", example = "report_abc")
        private String reportId;

        @JsonProperty("job_id")
        @Schema(description = "Reference to CommentAnalysisJob", example = "job_123456")
        private String jobId;

        @JsonProperty("post_id")
        @Schema(description = "Post identifier", example = "1")
        private Integer postId;

        @JsonProperty("generated_at")
        @Schema(description = "ISO timestamp when generated", example = "2025-08-26T12:00:20Z")
        private String generatedAt;

        @JsonProperty("summary")
        @Schema(description = "Human readable summary", example = "5 comments analyzed...")
        private String summary;

        @JsonProperty("metrics")
        @Schema(description = "Computed metrics")
        private MetricsDto metrics;

        @JsonProperty("recipient_email")
        @Schema(description = "Recipient email", example = "ops@example.com")
        private String recipientEmail;

        @JsonProperty("status")
        @Schema(description = "Status of report", example = "SENT")
        private String status;

        @JsonProperty("sent_at")
        @Schema(description = "ISO timestamp when sent", example = "2025-08-26T12:01:00Z")
        private String sentAt;
    }

    @Data
    @Schema(name = "MetricsDto", description = "Metrics object inside AnalysisReport")
    public static class MetricsDto {
        @JsonProperty("count")
        @Schema(description = "Number of comments analyzed", example = "5")
        private Integer count;

        @JsonProperty("avg_length_words")
        @Schema(description = "Average length in words", example = "42")
        private Integer avgLengthWords;

        @JsonProperty("sentiment_summary")
        @Schema(description = "Sentiment summary", example = "neutral")
        private String sentimentSummary;

        @JsonProperty("top_words")
        @Schema(description = "Top words", example = "[\"voluptate\",\"quia\"]")
        private List<String> topWords;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technicalId")
    public static class TechnicalIdResponse {
        @JsonProperty("technicalId")
        @Schema(description = "Technical UUID of the entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "SearchRequest", description = "Request to search entities by simple conditions")
    public static class SearchRequest {
        @Schema(description = "List of conditions to AND together")
        private List<ConditionDto> conditions;

        @Data
        public static class ConditionDto {
            @Schema(description = "Field name (use JSONPath or plain field name)", example = "report_id")
            private String fieldName;

            @Schema(description = "Operator (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.)", example = "EQUALS")
            private String operator;

            @Schema(description = "Value to compare", example = "report_abc")
            private String value;
        }
    }
}