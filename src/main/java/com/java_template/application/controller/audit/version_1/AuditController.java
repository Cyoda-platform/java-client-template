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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import com.java_template.application.entity.audit.version_1.Audit;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping(path = "/api/audit/v1")
@Tag(name = "Audit", description = "Audit entity proxy controller (v1)")
@RequiredArgsConstructor
public class AuditController {

    private static final Logger logger = LoggerFactory.getLogger(AuditController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "Create Audits", description = "Create Audit records in batch. Returns technicalIds (UUID) of created audits.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateAuditResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createAudits(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of Audit creation payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateAuditRequest.class))))
            @RequestBody List<CreateAuditRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("request body must be a non-empty array");
            }

            List<Audit> entities = new ArrayList<>();
            for (CreateAuditRequest request : requests) {
                if (request == null) continue;
                if (request.getAuditId() == null || request.getAuditId().isBlank()) {
                    throw new IllegalArgumentException("auditId is required for each audit");
                }
                if (request.getAction() == null || request.getAction().isBlank()) {
                    throw new IllegalArgumentException("action is required for each audit");
                }
                if (request.getActorId() == null || request.getActorId().isBlank()) {
                    throw new IllegalArgumentException("actorId is required for each audit");
                }
                if (request.getEntityRef() == null || request.getEntityRef().isBlank()) {
                    throw new IllegalArgumentException("entityRef is required for each audit");
                }
                if (request.getTimestamp() == null || request.getTimestamp().isBlank()) {
                    throw new IllegalArgumentException("timestamp is required for each audit");
                }

                Audit entity = new Audit();
                entity.setAuditId(request.getAuditId());
                entity.setAction(request.getAction());
                entity.setActorId(request.getActorId());
                entity.setEntityRef(request.getEntityRef());
                entity.setEvidenceRef(request.getEvidenceRef());
                entity.setMetadata(request.getMetadata());
                entity.setTimestamp(request.getTimestamp());
                entities.add(entity);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Audit.ENTITY_NAME,
                    Audit.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<String> technicalIds = new ArrayList<>();
            if (ids != null) {
                for (UUID u : ids) technicalIds.add(u != null ? u.toString() : null);
            }
            BatchCreateResponse response = new BatchCreateResponse();
            response.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create audits: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during create audits: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during create audits: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating audits", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception ex) {
            logger.error("Unexpected error when creating audits", ex);
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
    @Schema(description = "Batch create response with technicalIds")
    static class BatchCreateResponse {
        @Schema(description = "List of technical ids of created entities")
        private List<String> technicalIds;
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