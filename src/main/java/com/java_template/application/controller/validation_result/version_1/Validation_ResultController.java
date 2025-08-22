package com.java_template.application.controller.validation_result.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.validation_result.version_1.Validation_Result;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for Validation_Result entity (version 1)
 *
 * Notes:
 * - Controller acts as a proxy to EntityService only. No business logic implemented here.
 * - DTOs for request/response are defined as static classes below.
 */
@RestController
@RequestMapping("/api/validation-result/v1")
@Tag(name = "Validation_Result", description = "Operations for Validation_Result entity (v1)")
@RequiredArgsConstructor
public class Validation_ResultController {

    private static final Logger logger = LoggerFactory.getLogger(Validation_ResultController.class);

    private final EntityService entityService;

    @Operation(summary = "Add a Validation_Result", description = "Adds a single Validation_Result entity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/items")
    public ResponseEntity<?> addItem(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Validation_Result to add")
        @RequestBody ValidationResultRequest request
    ) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request body or data is null");
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                Validation_Result.ENTITY_NAME,
                String.valueOf(Validation_Result.ENTITY_VERSION),
                request.getData()
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for addItem: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in addItem", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding item", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in addItem", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Add multiple Validation_Result entities", description = "Adds multiple Validation_Result entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/items/batch")
    public ResponseEntity<?> addItems(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Validation_Result to add")
        @RequestBody ItemsRequest request
    ) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request body or data is null");
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                Validation_Result.ENTITY_NAME,
                String.valueOf(Validation_Result.ENTITY_VERSION),
                request.getData()
            );
            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new IdsResponse(ids));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for addItems: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in addItems", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding items", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in addItems", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get a Validation_Result by technicalId", description = "Retrieves a single Validation_Result by its technical UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetItemResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/items/{technicalId}")
    public ResponseEntity<?> getItem(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId is null");
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Validation_Result.ENTITY_NAME,
                String.valueOf(Validation_Result.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(new GetItemResponse(node));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for getItem: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in getItem", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting item", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getItem", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Validation_Result entities", description = "Retrieves all Validation_Result entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetItemsResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/items")
    public ResponseEntity<?> getItems() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Validation_Result.ENTITY_NAME,
                String.valueOf(Validation_Result.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(new GetItemsResponse(array));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in getItems", e);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting items", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getItems", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Validation_Result entities by search condition", description = "Retrieves Validation_Result entities that match the given search condition")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetItemsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/items/search")
    public ResponseEntity<?> getItemsByCondition(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request")
        @RequestBody SearchConditionRequest conditionRequest
    ) {
        try {
            if (conditionRequest == null) {
                throw new IllegalArgumentException("conditionRequest is null");
            }
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                Validation_Result.ENTITY_NAME,
                String.valueOf(Validation_Result.ENTITY_VERSION),
                conditionRequest,
                true
            );
            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(new GetItemsResponse(array));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in getItemsByCondition", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching items", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getItemsByCondition", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Update a Validation_Result", description = "Updates a single Validation_Result entity by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/items/{technicalId}")
    public ResponseEntity<?> updateItem(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Validation_Result with updated values")
        @RequestBody ValidationResultRequest request
    ) {
        try {
            if (technicalId == null || request == null || request.getData() == null) {
                throw new IllegalArgumentException("technicalId or request data is null");
            }
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                Validation_Result.ENTITY_NAME,
                String.valueOf(Validation_Result.ENTITY_VERSION),
                UUID.fromString(technicalId),
                request.getData()
            );
            UUID updatedId = updatedFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for updateItem: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in updateItem", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating item", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in updateItem", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete a Validation_Result", description = "Deletes a single Validation_Result entity by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/items/{technicalId}")
    public ResponseEntity<?> deleteItem(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId is null");
            }
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                Validation_Result.ENTITY_NAME,
                String.valueOf(Validation_Result.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(deletedId));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for deleteItem: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in deleteItem", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting item", e);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in deleteItem", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /*
     * Static DTO classes for requests/responses
     */

    @Data
    @Schema(description = "Request wrapper containing a Validation_Result")
    public static class ValidationResultRequest {
        @Schema(description = "Validation_Result entity", required = true)
        private Validation_Result data;
    }

    @Data
    @Schema(description = "Request wrapper containing list of Validation_Result entities")
    public static class ItemsRequest {
        @Schema(description = "List of Validation_Result entities", required = true)
        private List<Validation_Result> data;
    }

    @Data
    @Schema(description = "Response containing a single UUID")
    public static class IdResponse {
        @Schema(description = "Technical ID (UUID) of the entity")
        private UUID id;

        public IdResponse(UUID id) {
            this.id = id;
        }
    }

    @Data
    @Schema(description = "Response containing multiple UUIDs")
    public static class IdsResponse {
        @Schema(description = "List of technical IDs (UUIDs)")
        private List<UUID> ids;

        public IdsResponse(List<UUID> ids) {
            this.ids = ids;
        }
    }

    @Data
    @Schema(description = "Response containing a single item payload")
    public static class GetItemResponse {
        @Schema(description = "Entity payload as JSON")
        private ObjectNode data;

        public GetItemResponse(ObjectNode data) {
            this.data = data;
        }
    }

    @Data
    @Schema(description = "Response containing multiple items payload")
    public static class GetItemsResponse {
        @Schema(description = "Array of entity payloads as JSON")
        private ArrayNode data;

        public GetItemsResponse(ArrayNode data) {
            this.data = data;
        }
    }
}