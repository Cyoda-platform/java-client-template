package com.java_template.application.controller.searchfilter.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.searchfilter.version_1.SearchFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for SearchFilter entity (version 1)
 *
 * Responsibilities:
 * - Proxy requests to EntityService
 * - Validate basic request format
 * - Proper exception handling and response mapping
 *
 * Note: No business logic implemented in controller.
 */
@RestController
@RequestMapping("/api/v1/search-filter")
@Tag(name = "SearchFilter", description = "CRUD API for SearchFilter entity (v1)")
@Validated
public class SearchFilterController {

    private static final Logger logger = LoggerFactory.getLogger(SearchFilterController.class);

    private final EntityService entityService;

    public SearchFilterController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create SearchFilter", description = "Adds a single SearchFilter entity.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> addItem(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "SearchFilter to add", required = true,
            content = @Content(schema = @Schema(implementation = SearchFilter.class)))
        @RequestBody SearchFilter data) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                SearchFilter.ENTITY_NAME,
                String.valueOf(SearchFilter.ENTITY_VERSION),
                data
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for addItem", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding SearchFilter", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while adding SearchFilter", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Internal error"));
        }
    }

    @Operation(summary = "Create multiple SearchFilters", description = "Adds multiple SearchFilter entities.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/batch", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> addItems(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of SearchFilter to add", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SearchFilter.class))))
        @RequestBody List<SearchFilter> data) {
        try {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                SearchFilter.ENTITY_NAME,
                String.valueOf(SearchFilter.ENTITY_VERSION),
                data
            );
            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new IdsResponse(ids));
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for addItems", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding SearchFilters", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while adding SearchFilters", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Internal error"));
        }
    }

    @Operation(summary = "Get SearchFilter by ID", description = "Retrieves a SearchFilter by its technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> getItem(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                SearchFilter.ENTITY_NAME,
                String.valueOf(SearchFilter.ENTITY_VERSION),
                id
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId for getItem: {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while getting SearchFilter", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while getting SearchFilter", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Internal error"));
        }
    }

    @Operation(summary = "Get all SearchFilters", description = "Retrieves all SearchFilter entities.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = "application/json", params = {"!query"})
    public ResponseEntity<?> getItems() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                SearchFilter.ENTITY_NAME,
                String.valueOf(SearchFilter.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while getting SearchFilters", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while getting SearchFilters", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Internal error"));
        }
    }

    @Operation(summary = "Query SearchFilters", description = "Retrieves SearchFilter entities matching a SearchConditionRequest.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/query", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> getItemsByCondition(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
            content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
        @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                SearchFilter.ENTITY_NAME,
                String.valueOf(SearchFilter.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid search condition", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while querying SearchFilters", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while querying SearchFilters", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Internal error"));
        }
    }

    @Operation(summary = "Update SearchFilter", description = "Updates a SearchFilter entity by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> updateItem(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "SearchFilter with updated fields", required = true,
            content = @Content(schema = @Schema(implementation = SearchFilter.class)))
        @RequestBody SearchFilter data) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                SearchFilter.ENTITY_NAME,
                String.valueOf(SearchFilter.ENTITY_VERSION),
                id,
                data
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId));
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for updateItem: {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while updating SearchFilter", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while updating SearchFilter", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Internal error"));
        }
    }

    @Operation(summary = "Delete SearchFilter", description = "Deletes a SearchFilter by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> deleteItem(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                SearchFilter.ENTITY_NAME,
                String.valueOf(SearchFilter.ENTITY_VERSION),
                id
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(deletedId));
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId for deleteItem: {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while deleting SearchFilter", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Interrupted"));
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting SearchFilter", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Internal error"));
        }
    }

    private ResponseEntity<?> handleExecutionException(ExecutionException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found", ex);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(cause.getMessage()));
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Illegal argument in async operation", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(cause.getMessage()));
        } else {
            logger.error("ExecutionException (unwrap)", ex);
            String message = (cause != null) ? cause.getMessage() : ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(message));
        }
    }

    // Response / Error DTOs used by the controller
    @Data
    public static class IdResponse {
        @Schema(description = "Technical id of the entity")
        private final UUID id;
    }

    @Data
    public static class IdsResponse {
        @Schema(description = "List of technical ids created")
        private final List<UUID> ids;
    }

    @Data
    public static class ErrorResponse {
        @Schema(description = "Error message")
        private final String message;
    }
}