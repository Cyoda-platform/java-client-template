package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.common.service.EntityService;
import com.java_template.application.entity.report.version_1.Report;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/reports")
@Tag(name = "Reports")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Report", description = "Retrieves a generated Report by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getReport(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Report.ENTITY_NAME,
                    String.valueOf(Report.ENTITY_VERSION),
                    java.util.UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            ReportResponse resp = mapper.treeToValue(node, ReportResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid getReport request", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution error fetching Report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching Report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error fetching Report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    public static class ReportResponse {
        @Schema(description = "Report id")
        private String reportId;
        @Schema(description = "Reference to originating job")
        private String jobRef;
        @Schema(description = "Period from (ISO date)")
        private String periodFrom;
        @Schema(description = "Period to (ISO date)")
        private String periodTo;
        @Schema(description = "Metrics map")
        private java.util.Map<String, Object> metrics;
        @Schema(description = "Grouping buckets")
        private java.util.List<java.util.Map<String, Object>> groupingBuckets;
        @Schema(description = "Presentation type")
        private String presentationType;
        @Schema(description = "Generated at (ISO datetime)")
        private String generatedAt;
        @Schema(description = "Download URL")
        private String downloadUrl;
        @Schema(description = "Metadata")
        private java.util.Map<String, Object> metadata;
    }
}
