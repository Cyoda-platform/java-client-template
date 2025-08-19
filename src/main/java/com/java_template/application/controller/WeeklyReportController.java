package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/reports/weekly")
@Tag(name = "WeeklyReport Controller", description = "Retrieve WeeklyReport entities")
public class WeeklyReportController {
    private static final Logger logger = LoggerFactory.getLogger(WeeklyReportController.class);

    private final EntityService entityService;

    public WeeklyReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get WeeklyReport by technicalId", description = "Retrieve a WeeklyReport by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = WeeklyReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getWeeklyReportById(
            @Parameter(name = "technicalId", description = "Technical ID of the WeeklyReport") @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("technicalId is required");
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    WeeklyReport.ENTITY_NAME,
                    String.valueOf(WeeklyReport.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("WeeklyReport not found");
            }
            return ResponseEntity.ok(new WeeklyReportResponse(node));

        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("Execution failed", cause != null ? cause : ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Execution error");
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class WeeklyReportResponse {
        @Schema(description = "WeeklyReport JSON payload")
        private final JsonNode report;
    }
}
