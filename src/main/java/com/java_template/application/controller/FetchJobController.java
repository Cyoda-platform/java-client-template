package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs/fetchBooks")
@Tag(name = "FetchJob Controller", description = "Create and retrieve FetchJob orchestration entities")
public class FetchJobController {
    private static final Logger logger = LoggerFactory.getLogger(FetchJobController.class);

    private final EntityService entityService;

    public FetchJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create or trigger a FetchJob", description = "Create a new FetchJob orchestration entity or trigger a run")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createFetchJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "FetchJob creation payload") @RequestBody FetchJobRequest request
    ) {
        try {
            if (request == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("request body required");
            }

            // Basic validation
            if (request.getName() == null || request.getName().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("name is required");
            }
            // Map request to entity
            FetchJob job = new FetchJob();
            job.setName(request.getName());
            job.setRunDay(request.getRunDay());
            job.setRunTime(request.getRunTime());
            job.setTimezone(request.getTimezone());
            job.setRecurrence(request.getRecurrence());
            job.setRecipients(request.getRecipients());
            job.setTriggeredBy(request.getTriggeredBy());
            job.setParameters(request.getParameters());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    FetchJob.ENTITY_NAME,
                    String.valueOf(FetchJob.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));

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

    @Operation(summary = "Get FetchJob by technicalId", description = "Retrieve a FetchJob by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GenericEntityResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getFetchJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the FetchJob") @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("technicalId is required");
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    FetchJob.ENTITY_NAME,
                    String.valueOf(FetchJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("FetchJob not found");
            }
            return ResponseEntity.ok(new GenericEntityResponse(node));

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
    static class FetchJobRequest {
        @Schema(description = "Human name for the job")
        private String name;
        @Schema(description = "Run day (e.g., Wednesday)")
        private String runDay;
        @Schema(description = "Run time HH:mm")
        private String runTime;
        @Schema(description = "IANA timezone (e.g., UTC)")
        private String timezone;
        @Schema(description = "Recurrence (one-off | daily | weekly | cron-expression)")
        private String recurrence;
        @Schema(description = "Recipients emails")
        private java.util.List<String> recipients;
        @Schema(description = "Triggered by (manual | schedule)")
        private String triggeredBy;
        @Schema(description = "Job parameters")
        private java.util.Map<String, Object> parameters;
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical id of the created resource")
        private final String technicalId;
    }

    @Data
    static class GenericEntityResponse {
        @Schema(description = "Raw entity JSON")
        private final JsonNode entity;
    }
}
