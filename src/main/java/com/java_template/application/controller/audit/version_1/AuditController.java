package com.java_template.application.controller.audit.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit", description = "Controller for Audit entity (version 1). Proxy to EntityService only.")
public class AuditController {

    private static final Logger logger = LoggerFactory.getLogger(AuditController.class);

    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuditController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Audit", description = "Persist a single Audit entity and return its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createAudit(@io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(schema = @Schema(implementation = AuditRequest.class))) @RequestBody AuditRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            Audit audit = toEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Audit.ENTITY_NAME,
                    String.valueOf(Audit.ENTITY_VERSION),
                    audit
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to createAudit: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in createAudit", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Audits", description = "Persist multiple Audit entities and return their technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = IdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createAuditsBulk(@io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(array = @ArraySchema(schema = @Schema(implementation = AuditRequest.class)))) @RequestBody List<AuditRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request body must contain a non-empty list");
            List<Audit> entities = new ArrayList<>();
            for (AuditRequest r : requests) {
                entities.add(toEntity(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Audit.ENTITY_NAME,
                    String.valueOf(Audit.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<IdResponse> response = new ArrayList<>();
            for (UUID id : ids) response.add(new IdResponse(id.toString()));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to createAuditsBulk: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in createAuditsBulk", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Audit by technicalId", description = "Retrieve a single Audit entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AuditResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAuditById(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Audit.ENTITY_NAME,
                    String.valueOf(Audit.ENTITY_VERSION),
                    tid
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                throw new NoSuchElementException("Audit not found: " + technicalId);
            }
            AuditResponse resp = mapper.treeToValue(node, AuditResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId in getAuditById: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in getAuditById", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Audits", description = "Retrieve all Audit entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AuditResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAllAudits() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Audit.ENTITY_NAME,
                    String.valueOf(Audit.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<AuditResponse> list = new ArrayList<>();
            if (array != null) {
                for (JsonNode n : array) {
                    list.add(mapper.treeToValue(n, AuditResponse.class));
                }
            }
            return ResponseEntity.ok(list);
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in getAllAudits", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Audit", description = "Update an existing Audit entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateAudit(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId,
                                         @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(schema = @Schema(implementation = AuditRequest.class))) @RequestBody AuditRequest request) {
        try {
            UUID tid = UUID.fromString(technicalId);
            if (request == null) throw new IllegalArgumentException("Request body is required");
            Audit audit = toEntity(request);
            CompletableFuture<UUID> updated = entityService.updateItem(
                    Audit.ENTITY_NAME,
                    String.valueOf(Audit.ENTITY_VERSION),
                    tid,
                    audit
            );
            UUID id = updated.get();
            return ResponseEntity.ok(new IdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to updateAudit: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in updateAudit", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Audit", description = "Delete an Audit entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteAudit(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deleted = entityService.deleteItem(
                    Audit.ENTITY_NAME,
                    String.valueOf(Audit.ENTITY_VERSION),
                    tid
            );
            UUID id = deleted.get();
            return ResponseEntity.ok(new IdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId in deleteAudit: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in deleteAudit", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // Helper to map request DTO to entity
    private Audit toEntity(AuditRequest req) {
        Audit a = new Audit();
        a.setAudit_id(req.getAudit_id());
        a.setAction(req.getAction());
        a.setActor_id(req.getActor_id());
        a.setEntity_ref(req.getEntity_ref());
        a.setEvidence_ref(req.getEvidence_ref());
        a.setMetadata(req.getMetadata());
        a.setTimestamp(req.getTimestamp());
        return a;
    }

    // Handle ExecutionException by unwrapping cause
    private ResponseEntity<?> handleExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage());
            return ResponseEntity.status(404).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument in async execution: {}", cause.getMessage());
            return ResponseEntity.badRequest().body(cause.getMessage());
        } else {
            logger.error("ExecutionException in async call", e);
            return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
        }
    }

    // Static DTO classes

    @Data
    @Schema(name = "AuditRequest", description = "Request payload to create or update an Audit")
    public static class AuditRequest {
        @Schema(description = "Audit canonical id", example = "audit-123")
        private String audit_id;

        @Schema(description = "Action performed", example = "publish")
        private String action;

        @Schema(description = "Actor id (serialized UUID string)", example = "e7b8f6d2-27a1-4a4f-bf3a-1c2d3e4f5a6b")
        private String actor_id;

        @Schema(description = "Entity reference (entity id + type)", example = "post-123:Post")
        private String entity_ref;

        @Schema(description = "Evidence reference", example = "evidence-456")
        private String evidence_ref;

        @Schema(description = "Arbitrary metadata")
        private java.util.Map<String, Object> metadata;

        @Schema(description = "Timestamp ISO-8601", example = "2025-08-27T12:34:56Z")
        private String timestamp;
    }

    @Data
    @Schema(name = "AuditResponse", description = "Audit entity representation returned in responses")
    public static class AuditResponse {
        @Schema(description = "Audit canonical id", example = "audit-123")
        private String audit_id;

        @Schema(description = "Action performed", example = "publish")
        private String action;

        @Schema(description = "Actor id (serialized UUID string)", example = "e7b8f6d2-27a1-4a4f-bf3a-1c2d3e4f5a6b")
        private String actor_id;

        @Schema(description = "Entity reference (entity id + type)", example = "post-123:Post")
        private String entity_ref;

        @Schema(description = "Evidence reference", example = "evidence-456")
        private String evidence_ref;

        @Schema(description = "Arbitrary metadata")
        private java.util.Map<String, Object> metadata;

        @Schema(description = "Timestamp ISO-8601", example = "2025-08-27T12:34:56Z")
        private String timestamp;
    }

    @Data
    @Schema(name = "IdResponse", description = "Response containing technicalId")
    public static class IdResponse {
        @Schema(description = "Technical id (UUID as string)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public IdResponse() {}

        public IdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}