package com.java_template.application.controller.catfact.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/catfact/v1/catfacts")
@io.swagger.v3.oas.annotations.tags.Tag(name = "CatFact", description = "CatFact entity operations (proxy controller)")
public class CatFactController {

    private static final Logger logger = LoggerFactory.getLogger(CatFactController.class);

    private final EntityService entityService;

    public CatFactController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create CatFact", description = "Create a CatFact entity (triggers workflow). Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createCatFact(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "CatFact payload", required = true,
                    content = @Content(schema = @Schema(implementation = CatFactRequest.class)))
            @RequestBody CatFactRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            CatFact entity = mapRequestToEntity(request);

            UUID id = entityService.addItem(
                    CatFact.ENTITY_NAME,
                    String.valueOf(CatFact.ENTITY_VERSION),
                    entity
            ).get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createCatFact: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createCatFact", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating CatFact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createCatFact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple CatFacts", description = "Create multiple CatFact entities (triggers workflows). Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BatchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createCatFactsBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of CatFact payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CatFactRequest.class))))
            @RequestBody List<CatFactRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request list is required");

            List<CatFact> entities = new ArrayList<>();
            for (CatFactRequest r : requests) {
                entities.add(mapRequestToEntity(r));
            }

            List<UUID> ids = entityService.addItems(
                    CatFact.ENTITY_NAME,
                    String.valueOf(CatFact.ENTITY_VERSION),
                    entities
            ).get();

            BatchResponse resp = new BatchResponse();
            List<String> stringIds = new ArrayList<>();
            for (UUID u : ids) stringIds.add(u.toString());
            resp.setTechnicalIds(stringIds);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createCatFactsBatch: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createCatFactsBatch", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating CatFacts batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createCatFactsBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get CatFact by technicalId", description = "Retrieve a CatFact by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CatFactResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getCatFactById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);

            ObjectNode node = entityService.getItem(
                    CatFact.ENTITY_NAME,
                    String.valueOf(CatFact.ENTITY_VERSION),
                    id
            ).get();

            return ResponseEntity.ok(node);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for getCatFactById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getCatFactById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting CatFact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getCatFactById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all CatFacts", description = "Retrieve all CatFact entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CatFactResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllCatFacts() {
        try {
            ArrayNode arr = entityService.getItems(
                    CatFact.ENTITY_NAME,
                    String.valueOf(CatFact.ENTITY_VERSION)
            ).get();

            return ResponseEntity.ok(arr);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in getAllCatFacts", e);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting all CatFacts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getAllCatFacts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search CatFacts by condition", description = "Search CatFacts using simple field-based conditions.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CatFactResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchCatFacts(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");

            ArrayNode arr = entityService.getItemsByCondition(
                    CatFact.ENTITY_NAME,
                    String.valueOf(CatFact.ENTITY_VERSION),
                    condition,
                    true
            ).get();

            return ResponseEntity.ok(arr);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in searchCatFacts", e);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching CatFacts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in searchCatFacts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update CatFact", description = "Update a CatFact by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateCatFact(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "CatFact payload", required = true,
                    content = @Content(schema = @Schema(implementation = CatFactRequest.class)))
            @RequestBody CatFactRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            UUID id = UUID.fromString(technicalId);
            CatFact entity = mapRequestToEntity(request);

            UUID updated = entityService.updateItem(
                    CatFact.ENTITY_NAME,
                    String.valueOf(CatFact.ENTITY_VERSION),
                    id,
                    entity
            ).get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updated.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for updateCatFact: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateCatFact", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating CatFact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in updateCatFact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete CatFact", description = "Delete a CatFact by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteCatFact(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);

            UUID deleted = entityService.deleteItem(
                    CatFact.ENTITY_NAME,
                    String.valueOf(CatFact.ENTITY_VERSION),
                    id
            ).get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deleted.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for deleteCatFact: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteCatFact", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting CatFact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in deleteCatFact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Helper to map request DTO to entity (dumb mapping, no business logic)
    private CatFact mapRequestToEntity(CatFactRequest request) {
        try {
            CatFact entity = new CatFact();
            if (request.getFactId() != null) entity.setFactId(request.getFactId());
            if (request.getText() != null) entity.setText(request.getText());
            if (request.getFetchedDate() != null) entity.setFetchedDate(OffsetDateTime.parse(request.getFetchedDate()));
            if (request.getValidationStatus() != null) entity.setValidationStatus(request.getValidationStatus());
            if (request.getArchivedDate() != null) entity.setArchivedDate(OffsetDateTime.parse(request.getArchivedDate()));
            return entity;
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid date format: " + e.getMessage(), e);
        }
    }

    @Data
    @Schema(name = "CatFactRequest", description = "Payload to create/update a CatFact")
    public static class CatFactRequest {
        @Schema(description = "Provider id or generated id", example = "fact-123", required = true)
        private String factId;

        @Schema(description = "Fact content", example = "Cats sleep 70% of their lives", required = true)
        private String text;

        @Schema(description = "ISO timestamp when fetched", example = "2025-08-26T12:00:00Z", required = true)
        private String fetchedDate;

        @Schema(description = "Validation status (PENDING/VALID/INVALID)", example = "PENDING", required = true)
        private String validationStatus;

        @Schema(description = "ISO timestamp when archived (optional)", example = "2026-01-01T00:00:00Z", required = false)
        private String archivedDate;
    }

    @Data
    @Schema(name = "CatFactResponse", description = "CatFact response payload")
    public static class CatFactResponse {
        @Schema(description = "Technical id", example = "b3e1f9c2-...") 
        private String technicalId;

        @Schema(description = "Provider id or generated id", example = "fact-123")
        private String factId;

        @Schema(description = "Fact content", example = "Cats sleep 70% of their lives")
        private String text;

        @Schema(description = "ISO timestamp when fetched", example = "2025-08-26T12:00:00Z")
        private String fetchedDate;

        @Schema(description = "Validation status (PENDING/VALID/INVALID)", example = "VALID")
        private String validationStatus;

        @Schema(description = "ISO timestamp when archived (optional)", example = "2026-01-01T00:00:00Z")
        private String archivedDate;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing a technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id", example = "b3e1f9c2-...")
        private String technicalId;
    }

    @Data
    @Schema(name = "BatchResponse", description = "Response containing multiple technicalIds")
    public static class BatchResponse {
        @Schema(description = "List of technical ids")
        private List<String> technicalIds;
    }
}