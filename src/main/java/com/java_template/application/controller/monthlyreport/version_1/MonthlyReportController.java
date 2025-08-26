package com.java_template.application.controller.monthlyreport.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport;
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
@RequestMapping("/api/v1/monthly-reports")
@Tag(name = "MonthlyReport", description = "Controller proxy for MonthlyReport entity operations")
public class MonthlyReportController {

    private static final Logger logger = LoggerFactory.getLogger(MonthlyReportController.class);

    private final EntityService entityService;

    public MonthlyReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create MonthlyReport", description = "Create a new MonthlyReport entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createMonthlyReport(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "MonthlyReport payload", required = true,
            content = @Content(schema = @Schema(implementation = MonthlyReportRequest.class)))
                                               @RequestBody MonthlyReportRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            MonthlyReport entity = toEntity(request);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    MonthlyReport.ENTITY_NAME,
                    String.valueOf(MonthlyReport.ENTITY_VERSION),
                    entity
            );

            UUID id = idFuture.get();
            AddResponse resp = new AddResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request to create MonthlyReport", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating MonthlyReport", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating MonthlyReport", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple MonthlyReports", description = "Create multiple MonthlyReport entities in batch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BatchAddResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createMonthlyReportsBatch(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of MonthlyReport payloads", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MonthlyReportRequest.class))))
                                                       @RequestBody List<MonthlyReportRequest> requests) {
        try {
            if (requests == null) throw new IllegalArgumentException("Request body is required");
            List<MonthlyReport> entities = new ArrayList<>();
            for (MonthlyReportRequest req : requests) {
                entities.add(toEntity(req));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    MonthlyReport.ENTITY_NAME,
                    String.valueOf(MonthlyReport.ENTITY_VERSION),
                    entities
            );

            List<UUID> ids = idsFuture.get();
            BatchAddResponse resp = new BatchAddResponse();
            List<String> stringIds = new ArrayList<>();
            for (UUID u : ids) stringIds.add(u.toString());
            resp.setTechnicalIds(stringIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid batch create request for MonthlyReport", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while batch creating MonthlyReports", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while batch creating MonthlyReports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get MonthlyReport by technicalId", description = "Retrieve a MonthlyReport by its technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = MonthlyReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getMonthlyReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    MonthlyReport.ENTITY_NAME,
                    String.valueOf(MonthlyReport.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId for getMonthlyReport: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving MonthlyReport {}", technicalId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving MonthlyReport {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all MonthlyReports", description = "Retrieve all MonthlyReport entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = MonthlyReportResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllMonthlyReports() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    MonthlyReport.ENTITY_NAME,
                    String.valueOf(MonthlyReport.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving all MonthlyReports", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving all MonthlyReports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search MonthlyReports", description = "Retrieve MonthlyReport entities matching the provided search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = MonthlyReportResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchMonthlyReports(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    MonthlyReport.ENTITY_NAME,
                    String.valueOf(MonthlyReport.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid search condition for MonthlyReport search", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching MonthlyReports", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while searching MonthlyReports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update MonthlyReport", description = "Update an existing MonthlyReport entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateMonthlyReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "MonthlyReport payload", required = true,
                    content = @Content(schema = @Schema(implementation = MonthlyReportRequest.class)))
            @RequestBody MonthlyReportRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            UUID id = UUID.fromString(technicalId);
            MonthlyReport entity = toEntity(request);

            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    MonthlyReport.ENTITY_NAME,
                    String.valueOf(MonthlyReport.ENTITY_VERSION),
                    id,
                    entity
            );

            UUID updatedId = updatedFuture.get();
            UpdateResponse resp = new UpdateResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request to update MonthlyReport {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating MonthlyReport {}", technicalId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while updating MonthlyReport {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete MonthlyReport", description = "Delete a MonthlyReport entity by its technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteMonthlyReport(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    MonthlyReport.ENTITY_NAME,
                    String.valueOf(MonthlyReport.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedFuture.get();
            DeleteResponse resp = new DeleteResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId for deleteMonthlyReport: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting MonthlyReport {}", technicalId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while deleting MonthlyReport {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Utility to map request DTO to entity (no business logic)
    private MonthlyReport toEntity(MonthlyReportRequest req) {
        MonthlyReport m = new MonthlyReport();
        m.setAdminRecipients(req.getAdminRecipients());
        m.setDeliveryAttempts(req.getDeliveryAttempts());
        m.setGeneratedAt(req.getGeneratedAt());
        m.setInvalidRecordsCount(req.getInvalidRecordsCount());
        m.setMonth(req.getMonth());
        m.setNewUsers(req.getNewUsers());
        m.setPublishedStatus(req.getPublishedStatus());
        m.setReportFileRef(req.getReportFileRef());
        m.setTotalUsers(req.getTotalUsers());
        m.setUpdatedUsers(req.getUpdatedUsers());

        List<MonthlyReport.SampleRecord> sampleList = new ArrayList<>();
        if (req.getSampleRecords() != null) {
            for (SampleRecordRequest srq : req.getSampleRecords()) {
                MonthlyReport.SampleRecord sr = new MonthlyReport.SampleRecord();
                sr.setId(srq.getId());
                sr.setName(srq.getName());
                sr.setProcessingStatus(srq.getProcessingStatus());
                sampleList.add(sr);
            }
        }
        m.setSampleRecords(sampleList);
        return m;
    }

    // DTOs

    @Data
    @Schema(name = "MonthlyReportRequest", description = "Request payload for MonthlyReport")
    public static class MonthlyReportRequest {
        private List<String> adminRecipients;
        private Integer deliveryAttempts;
        private String generatedAt;
        private Integer invalidRecordsCount;
        private String month;
        private Integer newUsers;
        private String publishedStatus;
        private String reportFileRef;
        private List<SampleRecordRequest> sampleRecords;
        private Integer totalUsers;
        private Integer updatedUsers;
    }

    @Data
    @Schema(name = "SampleRecordRequest", description = "Sample record within MonthlyReport")
    public static class SampleRecordRequest {
        private Integer id;
        private String name;
        private String processingStatus;
    }

    @Data
    @Schema(name = "MonthlyReportResponse", description = "Response payload for MonthlyReport")
    public static class MonthlyReportResponse {
        private List<String> adminRecipients;
        private Integer deliveryAttempts;
        private String generatedAt;
        private Integer invalidRecordsCount;
        private String month;
        private Integer newUsers;
        private String publishedStatus;
        private String reportFileRef;
        private List<SampleRecordResponse> sampleRecords;
        private Integer totalUsers;
        private Integer updatedUsers;
    }

    @Data
    @Schema(name = "SampleRecordResponse", description = "Sample record in MonthlyReport response")
    public static class SampleRecordResponse {
        private Integer id;
        private String name;
        private String processingStatus;
    }

    @Data
    @Schema(name = "AddResponse", description = "Response containing created technicalId")
    public static class AddResponse {
        private String technicalId;
    }

    @Data
    @Schema(name = "BatchAddResponse", description = "Response containing created technicalIds")
    public static class BatchAddResponse {
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "UpdateResponse", description = "Response containing updated technicalId")
    public static class UpdateResponse {
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteResponse", description = "Response containing deleted technicalId")
    public static class DeleteResponse {
        private String technicalId;
    }
}