package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs")
@Tag(name = "Report Jobs")
public class ReportJobController {

    private static final Logger logger = LoggerFactory.getLogger(ReportJobController.class);
    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReportJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create a ReportJob", description = "Creates a new ReportJob and returns the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateReportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PostMapping(value = "/reports", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createReportJob(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Report job creation payload") @RequestBody CreateReportJobRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            ReportJob job = new ReportJob();
            job.setJobName(request.getJobName());
            job.setInitiatedBy(request.getInitiatedBy());
            job.setFilterDateFrom(request.getFilterDateFrom());
            job.setFilterDateTo(request.getFilterDateTo());
            job.setMinPrice(request.getMinPrice());
            job.setMaxPrice(request.getMaxPrice());
            job.setDepositPaid(request.getDepositPaid());
            job.setGrouping(request.getGrouping());
            job.setPresentationType(request.getPresentationType());

            // Controllers remain thin: set default status and createdAt to satisfy persistence requirements
            job.setStatus("PENDING");
            job.setCreatedAt(Instant.now().toString());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    job
            );

            UUID id = idFuture.get();
            CreateReportJobResponse resp = new CreateReportJobResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to create ReportJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution error creating ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get ReportJob", description = "Retrieves a ReportJob by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ReportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getReportJob(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ReportJob.ENTITY_NAME,
                    String.valueOf(ReportJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            ReportJobResponse resp = mapper.treeToValue(node, ReportJobResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid getReportJob request", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution error fetching ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error fetching ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    public static class CreateReportJobRequest {
        @Schema(description = "Human-friendly job name", example = "MonthlyRevenue")
        private String jobName;
        @Schema(description = "User or system that initiated the job", example = "alice")
        private String initiatedBy;
        @Schema(description = "Filter date from (ISO date)")
        private String filterDateFrom;
        @Schema(description = "Filter date to (ISO date)")
        private String filterDateTo;
        @Schema(description = "Minimum price filter")
        private Double minPrice;
        @Schema(description = "Maximum price filter")
        private Double maxPrice;
        @Schema(description = "Deposit paid filter")
        private Boolean depositPaid;
        @Schema(description = "Grouping: DAILY|WEEKLY|MONTHLY")
        private String grouping;
        @Schema(description = "Presentation type: TABLE|CHART")
        private String presentationType;
    }

    @Data
    public static class CreateReportJobResponse {
        @Schema(description = "Technical id of the created job")
        private String technicalId;
    }

    @Data
    public static class ReportJobResponse {
        @Schema(description = "Job technical id / name alias")
        private String jobName;
        @Schema(description = "Who initiated the job")
        private String initiatedBy;
        @Schema(description = "Filter date from")
        private String filterDateFrom;
        @Schema(description = "Filter date to")
        private String filterDateTo;
        @Schema(description = "Minimum price filter")
        private Double minPrice;
        @Schema(description = "Maximum price filter")
        private Double maxPrice;
        @Schema(description = "Deposit paid filter")
        private Boolean depositPaid;
        @Schema(description = "Grouping")
        private String grouping;
        @Schema(description = "Presentation type")
        private String presentationType;
        @Schema(description = "Status")
        private String status;
        @Schema(description = "Created at (ISO datetime)")
        private String createdAt;
        @Schema(description = "Completed at (ISO datetime)")
        private String completedAt;
        @Schema(description = "Error details if failed")
        private String errorDetails;
        @Schema(description = "Attached report id if completed")
        private String reportId;
    }
}
