package com.java_template.application.controller.monthlyreport.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "MonthlyReport", description = "APIs for MonthlyReport entity (proxy to EntityService)")
public class MonthlyReportController {

    private static final Logger logger = LoggerFactory.getLogger(MonthlyReportController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MonthlyReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get all MonthlyReports", description = "Retrieve all MonthlyReport entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MonthlyReportResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> getAllReports() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    MonthlyReport.ENTITY_NAME,
                    String.valueOf(MonthlyReport.ENTITY_VERSION)
            );
            ArrayNode arrayNode = itemsFuture.get();
            List<MonthlyReportResponse> response = objectMapper.convertValue(arrayNode,
                    new TypeReference<List<MonthlyReportResponse>>() {});
            return ResponseEntity.ok(response);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in getAllReports", cause);
            return ResponseEntity.status(500).body(cause.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in getAllReports", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Exception in getAllReports", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get MonthlyReport by technicalId", description = "Retrieve a MonthlyReport by its technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = MonthlyReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getReportById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    MonthlyReport.ENTITY_NAME,
                    String.valueOf(MonthlyReport.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(404).body("MonthlyReport not found");
            }
            MonthlyReportResponse response = objectMapper.convertValue(node, MonthlyReportResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid UUID or argument in getReportById", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in getReportById", cause);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in getReportById", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Exception in getReportById", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Create MonthlyReport", description = "Persist a MonthlyReport entity (proxy to EntityService)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "TechnicalId returned",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> createReport(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "MonthlyReport payload")
            @Valid @RequestBody MonthlyReportRequest request) {
        try {
            // Convert request to ObjectNode and pass through to EntityService
            ObjectNode node = objectMapper.convertValue(request, ObjectNode.class);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    MonthlyReport.ENTITY_NAME,
                    String.valueOf(MonthlyReport.ENTITY_VERSION),
                    node
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in createReport", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in createReport", cause);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in createReport", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Exception in createReport", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Update MonthlyReport", description = "Update a MonthlyReport entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "TechnicalId returned",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "MonthlyReport payload")
            @Valid @RequestBody MonthlyReportRequest request) {
        try {
            UUID id = UUID.fromString(technicalId);
            ObjectNode node = objectMapper.convertValue(request, ObjectNode.class);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    MonthlyReport.ENTITY_NAME,
                    String.valueOf(MonthlyReport.ENTITY_VERSION),
                    id,
                    node
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in updateReport", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in updateReport", cause);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in updateReport", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Exception in updateReport", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete MonthlyReport", description = "Delete a MonthlyReport entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "TechnicalId returned",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    MonthlyReport.ENTITY_NAME,
                    String.valueOf(MonthlyReport.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deletedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in deleteReport", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in deleteReport", cause);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in deleteReport", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Exception in deleteReport", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // Static DTOs

    @Data
    @Schema(name = "MonthlyReportRequest", description = "Request payload for MonthlyReport")
    public static class MonthlyReportRequest {
        @Schema(description = "Month covered by the report, format YYYY-MM", example = "2025-09")
        @NotBlank
        private String month;

        @Schema(description = "Timestamp when report was generated", example = "2025-09-01T00:05:00Z")
        private String generatedAt;

        @Schema(description = "Total users count", example = "100")
        private Integer totalUsers;

        @Schema(description = "New users count", example = "95")
        private Integer newUsers;

        @Schema(description = "Invalid users count", example = "5")
        private Integer invalidUsers;

        @Schema(description = "Reference to report file", example = "reports/2025-09-user-report.pdf")
        private String fileRef;

        @Schema(description = "Report status", example = "PUBLISHED")
        private String status;

        @Schema(description = "Timestamp when delivered to admin", example = "2025-09-01T00:06:00Z")
        private String deliveryAt;
    }

    @Data
    @Schema(name = "MonthlyReportResponse", description = "Response payload for MonthlyReport")
    public static class MonthlyReportResponse {
        @Schema(description = "Month covered by the report, format YYYY-MM", example = "2025-09")
        private String month;

        @Schema(description = "Timestamp when report was generated", example = "2025-09-01T00:05:00Z")
        private String generatedAt;

        @Schema(description = "Total users count", example = "100")
        private Integer totalUsers;

        @Schema(description = "New users count", example = "95")
        private Integer newUsers;

        @Schema(description = "Invalid users count", example = "5")
        private Integer invalidUsers;

        @Schema(description = "Reference to report file", example = "reports/2025-09-user-report.pdf")
        private String fileRef;

        @Schema(description = "Report status", example = "PUBLISHED")
        private String status;

        @Schema(description = "Timestamp when delivered to admin", example = "2025-09-01T00:06:00Z")
        private String deliveryAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical UUID of the entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}