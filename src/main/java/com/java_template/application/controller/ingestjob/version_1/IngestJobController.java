package com.java_template.application.controller.ingestjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.ingestjob.version_1.IngestJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1")
@Tag(name = "IngestJob", description = "Controller proxy for IngestJob entity operations")
public class IngestJobController {

    private static final Logger logger = LoggerFactory.getLogger(IngestJobController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IngestJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Ingest Job", description = "Persist a new IngestJob which triggers the ingest workflow. Returns the technicalId of the created job.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping(value = "/ingest-jobs", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createIngestJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Ingest job request", required = true, content = @Content(schema = @Schema(implementation = CreateIngestJobRequest.class)))
            @Valid @RequestBody CreateIngestJobRequest request
    ) {
        try {
            if (request == null || request.getHn_payload() == null) {
                throw new IllegalArgumentException("hn_payload is required");
            }

            // Build entity payload as ObjectNode for persistence proxying (no business logic here)
            ObjectNode entityNode = objectMapper.createObjectNode();
            entityNode.set("hn_payload", request.getHn_payload());
            if (request.getClient_id() != null) {
                entityNode.put("client_id", request.getClient_id());
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    IngestJob.ENTITY_NAME,
                    String.valueOf(IngestJob.ENTITY_VERSION),
                    entityNode
            );

            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());

            return ResponseEntity.status(HttpStatus.CREATED).body(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to create ingest job: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Create ingest job failed - not found: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Create ingest job failed - bad request: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating ingest job", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating ingest job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while creating ingest job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "Get Ingest Job by technicalId", description = "Retrieve an IngestJob by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IngestJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(value = "/jobs/{technicalId}", produces = "application/json")
    public ResponseEntity<?> getIngestJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);

            CompletableFuture<com.fasterxml.jackson.databind.node.ObjectNode> itemFuture = entityService.getItem(
                    IngestJob.ENTITY_NAME,
                    String.valueOf(IngestJob.ENTITY_VERSION),
                    uuid
            );

            ObjectNode item = itemFuture.get();

            IngestJobResponse resp = new IngestJobResponse();
            resp.setTechnicalId(technicalId);

            // Map possible fields from returned entity node (no business logic, simple proxy mapping)
            if (item.has("status") && !item.get("status").isNull()) {
                resp.setStatus(item.get("status").asText());
            }
            if (item.has("created_at") && !item.get("created_at").isNull()) {
                resp.setCreated_at(item.get("created_at").asText());
            }
            if (item.has("stored_item_technicalId") && !item.get("stored_item_technicalId").isNull()) {
                resp.setStored_item_technicalId(item.get("stored_item_technicalId").asText());
            }
            if (item.has("error_message") && !item.get("error_message").isNull()) {
                resp.setError_message(item.get("error_message").asText());
            }
            if (item.has("hn_payload") && !item.get("hn_payload").isNull()) {
                resp.setHn_payload(item.get("hn_payload"));
            }

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId for getIngestJobById: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Ingest job not found: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request when retrieving ingest job: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving ingest job", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving ingest job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving ingest job", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // Static DTO classes

    @Data
    static class CreateIngestJobRequest {
        @Schema(description = "Hacker News JSON payload received from client", required = true)
        private ObjectNode hn_payload;

        @Schema(description = "Optional identifier from caller")
        private String client_id;
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the created entity")
        private String technicalId;
    }

    @Data
    static class IngestJobResponse {
        @Schema(description = "Technical ID of the job")
        private String technicalId;

        @Schema(description = "Job status (PENDING / VALIDATING / PROCESSING / COMPLETED / FAILED)")
        private String status;

        @Schema(description = "Creation timestamp")
        private String created_at;

        @Schema(description = "Technical ID of the stored item produced by the job")
        private String stored_item_technicalId;

        @Schema(description = "Error message if the job failed")
        private String error_message;

        @Schema(description = "Original or normalized Hacker News payload")
        private JsonNode hn_payload;
    }
}