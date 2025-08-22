package com.java_template.application.controller.adoptionrequest.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for AdoptionRequest entity - version 1
 *
 * Responsibilities:
 *  - Proxy requests to EntityService without implementing business logic
 *  - Validate basic request formats
 *  - Convert path technicalId to UUID using UUID.fromString()
 *  - Handle ExecutionException unwrapping and return appropriate HTTP responses
 */
@RestController
@RequestMapping("/api/adoption-request/v1")
@Tag(name = "AdoptionRequest", description = "APIs for AdoptionRequest entity (v1)")
public class AdoptionRequestController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);

    private final EntityService entityService;

    public AdoptionRequestController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create AdoptionRequest", description = "Creates a single AdoptionRequest entity and returns its technical id")
    @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = IdResponse.class)))
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> addItem(
            @RequestBody(description = "AdoptionRequest to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody CreateRequest request) {
        try {
            if (request == null || request.getEntity() == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            AdoptionRequest data = request.getEntity();
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    data
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request in addItem: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating AdoptionRequest", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error in addItem", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Operation(summary = "Create multiple AdoptionRequests", description = "Creates multiple AdoptionRequest entities and returns their technical ids")
    @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = IdsResponse.class)))
    @PostMapping(path = "/batch", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> addItems(
            @RequestBody(description = "List of AdoptionRequest entities", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateMultipleRequest.class))))
            @org.springframework.web.bind.annotation.RequestBody CreateMultipleRequest request) {
        try {
            if (request == null || request.getEntities() == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            List<AdoptionRequest> data = request.getEntities();
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    data
            );
            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new IdsResponse(ids));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request in addItems: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating multiple AdoptionRequests", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error in addItems", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Operation(summary = "Get AdoptionRequest by technicalId", description = "Retrieves a single AdoptionRequest by its technical id")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class)))
    @GetMapping(path = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> getItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request in getItem: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching AdoptionRequest", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error in getItem", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Operation(summary = "Get all AdoptionRequests", description = "Retrieves all AdoptionRequest entities")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class))))
    @GetMapping(produces = "application/json", params = "all")
    public ResponseEntity<?> getItemsAll() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request in getItemsAll: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching all AdoptionRequests", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error in getItemsAll", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Operation(summary = "Search AdoptionRequests by condition", description = "Searches AdoptionRequest entities using simple field-based conditions")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class))))
    @PostMapping(path = "/search", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> getItemsByCondition(
            @RequestBody(description = "Search condition request", required = true,
                    content = @Content(schema = @Schema(implementation = SearchRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody SearchRequest request) {
        try {
            if (request == null || request.getCondition() == null) {
                throw new IllegalArgumentException("Search condition is required");
            }
            SearchConditionRequest condition = request.getCondition();
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request in getItemsByCondition: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching AdoptionRequests", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error in getItemsByCondition", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Operation(summary = "Update AdoptionRequest", description = "Updates an existing AdoptionRequest entity by technical id")
    @ApiResponse(responseCode = "200", description = "Updated", content = @Content(schema = @Schema(implementation = IdResponse.class)))
    @PutMapping(path = "/{technicalId}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> updateItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @RequestBody(description = "AdoptionRequest to update", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdateRequest request) {
        try {
            if (request == null || request.getEntity() == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            UUID id = UUID.fromString(technicalId);
            AdoptionRequest data = request.getEntity();
            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    id,
                    data
            );
            UUID resultId = updatedId.get();
            return ResponseEntity.ok(new IdResponse(resultId));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request in updateItem: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating AdoptionRequest", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error in updateItem", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Operation(summary = "Delete AdoptionRequest", description = "Deletes an AdoptionRequest entity by technical id")
    @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(implementation = IdResponse.class)))
    @DeleteMapping(path = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> deleteItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    id
            );
            UUID resultId = deletedId.get();
            return ResponseEntity.ok(new IdResponse(resultId));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request in deleteItem: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting AdoptionRequest", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error in deleteItem", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ex.getMessage()));
        }
    }

    private ResponseEntity<ErrorResponse> handleExecutionException(ExecutionException ee) {
        Throwable cause = ee.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage(), cause);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(cause.getMessage()));
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument: {}", cause.getMessage(), cause);
            return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
        } else {
            logger.error("Execution exception occurred", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
        }
    }

    // --- Static DTO classes required by the controller and Swagger ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "CreateRequest", description = "Create single AdoptionRequest payload")
    public static class CreateRequest {
        @Schema(description = "AdoptionRequest entity", required = true, implementation = AdoptionRequest.class)
        private AdoptionRequest entity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "CreateMultipleRequest", description = "Create multiple AdoptionRequest payload")
    public static class CreateMultipleRequest {
        @Schema(description = "List of AdoptionRequest entities", required = true)
        private List<AdoptionRequest> entities;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "UpdateRequest", description = "Update AdoptionRequest payload")
    public static class UpdateRequest {
        @Schema(description = "AdoptionRequest entity", required = true, implementation = AdoptionRequest.class)
        private AdoptionRequest entity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "SearchRequest", description = "Search condition request")
    public static class SearchRequest {
        @Schema(description = "Search condition request", required = true, implementation = SearchConditionRequest.class)
        private SearchConditionRequest condition;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "IdResponse", description = "Response carrying a single technical id")
    public static class IdResponse {
        @Schema(description = "Technical id", required = true)
        private UUID id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "IdsResponse", description = "Response carrying multiple technical ids")
    public static class IdsResponse {
        @Schema(description = "List of technical ids", required = true)
        private List<UUID> ids;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ErrorResponse", description = "Error response")
    public static class ErrorResponse {
        @Schema(description = "Error message")
        private String message;
    }
}