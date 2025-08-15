package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.common.service.EntityService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/import-jobs")
@Tag(name = "ImportJob")
public class ImportJobController {
    private static final Logger logger = LoggerFactory.getLogger(ImportJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ImportJobController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create ImportJob", description = "Create an ImportJob and trigger asynchronous processing. Returns the technicalId of the created job.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createImportJob(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Import job request") @RequestBody CreateImportJobRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("request body is required");
            if (request.getJobName() == null || request.getJobName().isBlank()) throw new IllegalArgumentException("jobName is required");
            if (request.getPayload() == null || request.getPayload().isNull()) throw new IllegalArgumentException("payload is required");

            ImportJob job = new ImportJob();
            job.setJobName(request.getJobName());
            job.setSource(request.getSource());
            // store payload verbatim as string
            String payloadString = objectMapper.writeValueAsString(request.getPayload());
            job.setPayload(payloadString);
            job.setStatus("PENDING");
            job.setCreatedAt(Instant.now());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating ImportJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating ImportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating ImportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get ImportJob by technicalId", description = "Retrieve job status and metrics by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ImportJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(path = "/{technicalId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getImportJob(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ImportJob.ENTITY_NAME,
                    String.valueOf(ImportJob.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("ImportJob not found");
            }
            ImportJobResponse resp = new ImportJobResponse();
            resp.setTechnicalId(technicalId);
            if (node.hasNonNull("jobName")) resp.setJobName(node.get("jobName").asText());
            if (node.hasNonNull("source")) resp.setSource(node.get("source").asText());
            if (node.hasNonNull("status")) resp.setStatus(node.get("status").asText());
            if (node.hasNonNull("itemsCreatedCount")) resp.setItemsCreatedCount(node.get("itemsCreatedCount").asInt());
            if (node.hasNonNull("createdAt")) resp.setCreatedAt(node.get("createdAt").asText());
            if (node.hasNonNull("completedAt")) resp.setCompletedAt(node.get("completedAt").asText());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching ImportJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching ImportJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while fetching ImportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class CreateImportJobRequest {
        @Schema(description = "Human friendly name for the job", required = true)
        private String jobName;
        @Schema(description = "Optional source identifier")
        private String source;
        @Schema(description = "Raw Firebase-format Hacker News JSON object", required = true)
        private JsonNode payload;
    }

    @Data
    static class CreateResponse {
        @Schema(description = "Technical id of the created entity")
        private String technicalId;
    }

    @Data
    static class ImportJobResponse {
        @Schema(description = "Technical id of the job")
        private String technicalId;
        @Schema(description = "Job name")
        private String jobName;
        @Schema(description = "Source of the job")
        private String source;
        @Schema(description = "Status of the job (PENDING, IN_PROGRESS, COMPLETED, FAILED)")
        private String status;
        @Schema(description = "Number of items created")
        private Integer itemsCreatedCount;
        @Schema(description = "When the job was created")
        private String createdAt;
        @Schema(description = "When the job completed")
        private String completedAt;
    }
}
