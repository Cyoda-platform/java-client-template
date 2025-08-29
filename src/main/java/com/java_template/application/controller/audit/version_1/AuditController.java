package com.java_template.application.controller.audit.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import com.java_template.application.entity.audit.version_1.Audit;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(path = "/api/audit/v1")
@Tag(name = "Audit", description = "Audit entity proxy controller (v1)")
@RequiredArgsConstructor
public class AuditController {

    private static final Logger logger = LoggerFactory.getLogger(AuditController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "Create Audit", description = "Create an Audit record. Returns the technicalId (UUID) of the created audit.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateAuditResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createAudit(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Audit creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateAuditRequest.class)))
            @RequestBody CreateAuditRequest request) {
        try {
            // Basic validation of request format
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getAuditId() == null || request.getAuditId().isBlank()) {
                throw new IllegalArgumentException("auditId is required");
            }
            if (request.getAction() == null || request.getAction().isBlank()) {
                throw new IllegalArgumentException("action is required");
            }
            if (request.getActorId() == null || request.getActorId().isBlank()) {
                throw new IllegalArgumentException("actorId is required");
            }
            if (request.getEntityRef() == null || request.getEntityRef().isBlank()) {
                throw new IllegalArgumentException("entityRef is required");
            }
            if (request.getTimestamp() == null || request.getTimestamp().isBlank()) {
                throw new IllegalArgumentException("timestamp is required");
            }

            Audit entity = new Audit();
            entity.setAuditId(request.getAuditId());
            entity.setAction(request.getAction());
            entity.setActorId(request.getActorId());
            entity.setEntityRef(request.getEntityRef());
            entity.setEvidenceRef(request.getEvidenceRef());
            entity.setMetadata(request.getMetadata());
            entity.setTimestamp(request.getTimestamp());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Audit.ENTITY_NAME,
                    Audit.ENTITY_VERSION,
                    entity
            );
            UUID createdId = idFuture.get();

            CreateAuditResponse response = new CreateAuditResponse();
            response.setTechnicalId(createdId.toString());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create audit: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during create audit: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during create audit: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating audit", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception ex) {
            logger.error("Unexpected error when creating audit", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get Audit by technicalId", description = "Retrieve an Audit record by its technicalId (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AuditResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> getAuditById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(id);
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(404).body("Audit not found");
            }

            AuditResponse response = objectMapper.treeToValue(dataPayload.getData(), AuditResponse.class);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get audit: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Audit not found: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument when fetching audit: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when fetching audit", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception ex) {
            logger.error("Unexpected error when fetching audit", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Data
    @Schema(name = "CreateAuditRequest", description = "Payload to create an Audit")
    static class CreateAuditRequest {
        @Schema(description = "Audit technical id (UUID as string)", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String auditId;

        @Schema(description = "Action performed", required = true, example = "submit_for_review")
        private String action;

        @Schema(description = "Actor who performed the action (UUID as string)", required = true, example = "9b2f1e10-1d4b-4f8a-9f7e-1234567890ab")
        private String actorId;

        @Schema(description = "Reference to the entity affected", required = true, example = "post-123:Post")
        private String entityRef;

        @Schema(description = "Optional evidence reference", required = false)
        private String evidenceRef;

        @Schema(description = "Additional metadata about the audit event", required = false)
        private Map<String, Object> metadata;

        @Schema(description = "ISO-8601 timestamp string", required = true, example = "2025-08-29T12:34:56Z")
        private String timestamp;
    }

    @Data
    @Schema(name = "CreateAuditResponse", description = "Response after creating an Audit")
    static class CreateAuditResponse {
        @Schema(description = "Technical id of created entity (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "AuditResponse", description = "Audit entity representation returned by API")
    static class AuditResponse {
        @Schema(description = "Audit technical id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String auditId;

        @Schema(description = "Action performed", example = "submit_for_review")
        private String action;

        @Schema(description = "Actor id", example = "9b2f1e10-1d4b-4f8a-9f7e-1234567890ab")
        private String actorId;

        @Schema(description = "Reference to the entity affected", example = "post-123:Post")
        private String entityRef;

        @Schema(description = "Evidence reference")
        private String evidenceRef;

        @Schema(description = "Additional metadata")
        private Map<String, Object> metadata;

        @Schema(description = "ISO-8601 timestamp")
        private String timestamp;
    }
}