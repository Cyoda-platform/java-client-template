package com.java_template.application.controller.report.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.java_template.application.entity.report.version_1.Report;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.*;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/report/v1/reports")
@Tag(name = "Report", description = "Report entity proxy API (version 1)")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Report", description = "Persist a Report entity (proxy to EntityService).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ReportCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createReport(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Report payload") @RequestBody ReportRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Report entity = mapRequestToEntity(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Report.ENTITY_NAME,
                    Report.ENTITY_VERSION,
                    entity
            );
            UUID id = idFuture.get();

            ReportCreateResponse resp = new ReportCreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to create report: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating report", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating report", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error when creating report", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Reports", description = "Persist multiple Report entities (proxy to EntityService).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportCreateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createReportsBatch(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of Report payloads") @RequestBody List<ReportRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request list is required");

            List<Report> entities = new ArrayList<>();
            for (ReportRequest r : requests) {
                entities.add(mapRequestToEntity(r));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Report.ENTITY_NAME,
                    Report.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();

            List<ReportCreateResponse> responses = new ArrayList<>();
            for (UUID id : ids) {
                ReportCreateResponse resp = new ReportCreateResponse();
                resp.setTechnicalId(id.toString());
                responses.add(resp);
            }
            return ResponseEntity.ok(responses);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid batch create request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating reports batch", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating reports batch", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error when creating reports batch", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Report by technicalId", description = "Retrieve a Report by technicalId (proxy to EntityService).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getReportById(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            Object payloadData = dataPayload != null ? dataPayload.getData() : null;
            if (payloadData == null) {
                return ResponseEntity.status(404).body("Report not found");
            }
            JsonNode dataNode = (JsonNode) dataPayload.getData();
            ReportResponse resp = objectMapper.treeToValue(dataNode, ReportResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid get request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when getting report", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when getting report", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error when getting report", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Query Reports", description = "Retrieve all Reports (proxy to EntityService).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getReports() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Report.ENTITY_NAME,
                    Report.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<ReportResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = (JsonNode) payload.getData();
                    ReportResponse resp = objectMapper.treeToValue(data, ReportResponse.class);
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when listing reports", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when listing reports", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error when listing reports", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Reports by condition", description = "Retrieve Reports matching a SearchConditionRequest (proxy to EntityService).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReportResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchReports(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "SearchConditionRequest payload") @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");
            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    Report.ENTITY_NAME,
                    Report.ENTITY_VERSION,
                    condition,
                    true
            );
            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            List<ReportResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = (JsonNode) payload.getData();
                    ReportResponse resp = objectMapper.treeToValue(data, ReportResponse.class);
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when searching reports", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when searching reports", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error when searching reports", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Report", description = "Update a Report entity (proxy to EntityService).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ReportUpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateReport(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
                                          @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Report payload") @RequestBody ReportRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Report entity = mapRequestToEntity(request);
            CompletableFuture<UUID> updatedId = entityService.updateItem(UUID.fromString(technicalId), entity);
            UUID id = updatedId.get();
            ReportUpdateResponse resp = new ReportUpdateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid update request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when updating report", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when updating report", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error when updating report", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Report", description = "Delete a Report entity by technicalId (proxy to EntityService).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ReportDeleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteReport(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<UUID> deletedId = entityService.deleteItem(UUID.fromString(technicalId));
            UUID id = deletedId.get();
            ReportDeleteResponse resp = new ReportDeleteResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid delete request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when deleting report", e);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when deleting report", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error when deleting report", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // Helper to map request DTO to entity using setters (no reflection)
    private Report mapRequestToEntity(ReportRequest request) {
        Report entity = new Report();
        entity.setAverageBookingPrice(request.getAverageBookingPrice());
        entity.setBookingsCount(request.getBookingsCount());
        entity.setCreatedBy(request.getCreatedBy());
        entity.setGeneratedAt(request.getGeneratedAt());
        entity.setJobTechnicalId(request.getJobTechnicalId());
        entity.setName(request.getName());
        entity.setReportId(request.getReportId());
        entity.setStatus(request.getStatus());
        entity.setTotalRevenue(request.getTotalRevenue());
        entity.setVisualizationUrl(request.getVisualizationUrl());

        // Criteria
        if (request.getCriteria() != null) {
            Report.Criteria c = new Report.Criteria();
            c.setDateFrom(request.getCriteria().getDateFrom());
            c.setDateTo(request.getCriteria().getDateTo());
            c.setDepositPaid(request.getCriteria().getDepositPaid());
            c.setMaxPrice(request.getCriteria().getMaxPrice());
            c.setMinPrice(request.getCriteria().getMinPrice());
            entity.setCriteria(c);
        }

        // Bookings sample
        if (request.getBookingsSample() != null) {
            List<Report.BookingSummary> summaries = new ArrayList<>();
            for (ReportRequest.BookingSummaryRequest bsReq : request.getBookingsSample()) {
                Report.BookingSummary bs = new Report.BookingSummary();
                bs.setAdditionalneeds(bsReq.getAdditionalneeds());
                bs.setBookingId(bsReq.getBookingId());
                bs.setCheckin(bsReq.getCheckin());
                bs.setCheckout(bsReq.getCheckout());
                bs.setDepositpaid(bsReq.getDepositpaid());
                bs.setFirstname(bsReq.getFirstname());
                bs.setLastname(bsReq.getLastname());
                bs.setPersistedAt(bsReq.getPersistedAt());
                bs.setSource(bsReq.getSource());
                bs.setTotalprice(bsReq.getTotalprice());
                summaries.add(bs);
            }
            entity.setBookingsSample(summaries);
        }
        return entity;
    }

    // DTOs

    @Data
    @Schema(name = "ReportRequest", description = "Report create/update request")
    public static class ReportRequest {
        @Schema(description = "Business report id")
        private String reportId;
        @Schema(description = "Link to ReportJob technical id")
        private String jobTechnicalId;
        @Schema(description = "Report name")
        private String name;
        @Schema(description = "User who created the report")
        private String createdBy;
        @Schema(description = "Criteria summary")
        private CriteriaRequest criteria;
        @Schema(description = "Total revenue")
        private Double totalRevenue;
        @Schema(description = "Average booking price")
        private Double averageBookingPrice;
        @Schema(description = "Bookings count")
        private Integer bookingsCount;
        @Schema(description = "Sample of bookings")
        private List<BookingSummaryRequest> bookingsSample;
        @Schema(description = "Visualization URL")
        private String visualizationUrl;
        @Schema(description = "Generated timestamp")
        private String generatedAt;
        @Schema(description = "Status")
        private String status;

        @Data
        @Schema(name = "CriteriaRequest", description = "Report criteria")
        public static class CriteriaRequest {
            @Schema(description = "Date from (ISO)")
            private String dateFrom;
            @Schema(description = "Date to (ISO)")
            private String dateTo;
            @Schema(description = "Deposit paid filter")
            private Boolean depositPaid;
            @Schema(description = "Max price")
            private Integer maxPrice;
            @Schema(description = "Min price")
            private Integer minPrice;
        }

        @Data
        @Schema(name = "BookingSummaryRequest", description = "Booking summary in report")
        public static class BookingSummaryRequest {
            @Schema(description = "Additional needs")
            private String additionalneeds;
            @Schema(description = "Booking id")
            private Integer bookingId;
            @Schema(description = "Checkin date")
            private String checkin;
            @Schema(description = "Checkout date")
            private String checkout;
            @Schema(description = "Deposit paid")
            private Boolean depositpaid;
            @Schema(description = "Firstname")
            private String firstname;
            @Schema(description = "Lastname")
            private String lastname;
            @Schema(description = "Persisted at timestamp")
            private String persistedAt;
            @Schema(description = "Source")
            private String source;
            @Schema(description = "Total price")
            private Double totalprice;
        }
    }

    @Data
    @Schema(name = "ReportResponse", description = "Report response payload")
    public static class ReportResponse {
        @Schema(description = "Business report id")
        private String reportId;
        @Schema(description = "Link to ReportJob technical id")
        private String jobTechnicalId;
        @Schema(description = "Report name")
        private String name;
        @Schema(description = "User who created the report")
        private String createdBy;
        @Schema(description = "Criteria summary")
        private ReportRequest.CriteriaRequest criteria;
        @Schema(description = "Total revenue")
        private Double totalRevenue;
        @Schema(description = "Average booking price")
        private Double averageBookingPrice;
        @Schema(description = "Bookings count")
        private Integer bookingsCount;
        @Schema(description = "Sample of bookings")
        private List<ReportRequest.BookingSummaryRequest> bookingsSample;
        @Schema(description = "Visualization URL")
        private String visualizationUrl;
        @Schema(description = "Generated timestamp")
        private String generatedAt;
        @Schema(description = "Status")
        private String status;
    }

    @Data
    @Schema(name = "ReportCreateResponse", description = "Report create response containing technical id")
    public static class ReportCreateResponse {
        @Schema(description = "Technical id returned by EntityService")
        private String technicalId;
    }

    @Data
    @Schema(name = "ReportUpdateResponse", description = "Report update response containing technical id")
    public static class ReportUpdateResponse {
        @Schema(description = "Technical id returned by EntityService")
        private String technicalId;
    }

    @Data
    @Schema(name = "ReportDeleteResponse", description = "Report delete response containing technical id")
    public static class ReportDeleteResponse {
        @Schema(description = "Technical id returned by EntityService")
        private String technicalId;
    }
}