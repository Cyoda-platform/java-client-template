package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.common.service.EntityService;
import com.java_template.application.entity.analysisreport.version_1.AnalysisReport;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/reports")
@Tag(name = "AnalysisReport", description = "Endpoints for Analysis Reports")
public class AnalysisReportController {
    private final Logger logger = LoggerFactory.getLogger(AnalysisReportController.class);
    private final EntityService entityService;

    public AnalysisReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get AnalysisReport", description = "Retrieve an AnalysisReport by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AnalysisReport.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<ObjectNode> getReport(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    AnalysisReport.ENTITY_NAME,
                    String.valueOf(AnalysisReport.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request to get report", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("Execution exception while retrieving report", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
