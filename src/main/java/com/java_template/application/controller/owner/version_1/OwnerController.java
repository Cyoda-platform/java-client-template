package com.java_template.application.controller.owner.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
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
import org.springframework.web.bind.annotation.*;

/**
 * Controller for Owner entity (version 1)
 *
 * Responsibilities:
 * - Proxy calls to EntityService, do not implement business logic here.
 * - Validate basic request formats and handle ExecutionException unwrapping.
 */
@RestController
@RequestMapping("/api/owner/v1")
@Tag(name = "Owner", description = "Owner entity APIs (v1)")
public class OwnerController {

    private static final Logger logger = LoggerFactory.getLogger(OwnerController.class);

    private final EntityService entityService;

    public OwnerController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create owner", description = "Create single Owner entity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = OwnerCreateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createOwner(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner payload", required = true,
            content = @Content(schema = @Schema(implementation = OwnerCreateRequest.class)))
        @RequestBody OwnerCreateRequest request) {
        try {
            if (request == null || request.owner == null) {
                throw new IllegalArgumentException("Missing owner payload");
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Owner.ENTITY_NAME,
                String.valueOf(Owner.ENTITY_VERSION),
                request.owner
            );
            UUID id = idFuture.get();
            OwnerCreateResponse resp = new OwnerCreateResponse();
            resp.id = id;
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid createOwner request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while creating owner", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple owners", description = "Create multiple Owner entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = OwnerCreateMultipleResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createOwnersBulk(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of owner payloads", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerCreateRequest.class))))
        @RequestBody OwnerCreateMultipleRequest request) {
        try {
            if (request == null || request.owners == null) {
                throw new IllegalArgumentException("Missing owners payload");
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                Owner.ENTITY_NAME,
                String.valueOf(Owner.ENTITY_VERSION),
                request.owners
            );
            List<UUID> ids = idsFuture.get();
            OwnerCreateMultipleResponse resp = new OwnerCreateMultipleResponse();
            resp.ids = ids;
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid createOwnersBulk request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while creating owners bulk", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating owners bulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating owners bulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get owner by technical ID", description = "Retrieve a single Owner by its technical UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = OwnerGetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getOwnerById(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Owner.ENTITY_NAME,
                String.valueOf(Owner.ENTITY_VERSION),
                id
            );
            ObjectNode ownerNode = itemFuture.get();
            OwnerGetResponse resp = new OwnerGetResponse();
            resp.owner = ownerNode;
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for getOwnerById: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while retrieving owner", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all owners", description = "Retrieve all Owner entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerGetResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllOwners() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Owner.ENTITY_NAME,
                String.valueOf(Owner.ENTITY_VERSION)
            );
            ArrayNode arrayNode = itemsFuture.get();
            OwnerListResponse resp = new OwnerListResponse();
            resp.owners = arrayNode;
            return ResponseEntity.ok(resp);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while retrieving all owners", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving all owners", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving all owners", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search owners by condition", description = "Retrieve Owner entities that match given search condition")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerGetResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchOwners(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true,
            content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
        @RequestBody SearchConditionRequest conditionRequest) {
        try {
            if (conditionRequest == null) {
                throw new IllegalArgumentException("Missing search condition");
            }
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                Owner.ENTITY_NAME,
                String.valueOf(Owner.ENTITY_VERSION),
                conditionRequest,
                true
            );
            ArrayNode arrayNode = filteredItemsFuture.get();
            OwnerListResponse resp = new OwnerListResponse();
            resp.owners = arrayNode;
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid searchOwners request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while searching owners", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching owners", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while searching owners", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update owner", description = "Update an existing Owner entity by technical ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = OwnerUpdateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateOwner(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner payload for update", required = true,
            content = @Content(schema = @Schema(implementation = OwnerUpdateRequest.class)))
        @RequestBody OwnerUpdateRequest request) {
        try {
            if (request == null || request.owner == null) {
                throw new IllegalArgumentException("Missing owner payload");
            }
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                Owner.ENTITY_NAME,
                String.valueOf(Owner.ENTITY_VERSION),
                id,
                request.owner
            );
            UUID updatedId = updatedIdFuture.get();
            OwnerUpdateResponse resp = new OwnerUpdateResponse();
            resp.id = updatedId;
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid updateOwner request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while updating owner", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while updating owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete owner", description = "Delete an Owner entity by technical ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = OwnerDeleteResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteOwner(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                Owner.ENTITY_NAME,
                String.valueOf(Owner.ENTITY_VERSION),
                id
            );
            UUID deletedId = deletedIdFuture.get();
            OwnerDeleteResponse resp = new OwnerDeleteResponse();
            resp.id = deletedId;
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for deleteOwner: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while deleting owner", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while deleting owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // -------------------------
    // Request / Response DTOs
    // -------------------------

    @Data
    @Schema(name = "OwnerCreateRequest", description = "Request to create an Owner entity")
    public static class OwnerCreateRequest {
        @Schema(description = "Owner entity payload as JSON", required = true)
        private ObjectNode owner;
    }

    @Data
    @Schema(name = "OwnerCreateResponse", description = "Response after creating an Owner entity")
    public static class OwnerCreateResponse {
        @Schema(description = "Technical UUID of the created entity")
        private UUID id;
    }

    @Data
    @Schema(name = "OwnerCreateMultipleRequest", description = "Request to create multiple Owner entities")
    public static class OwnerCreateMultipleRequest {
        @Schema(description = "List of Owner payloads", required = true)
        private List<ObjectNode> owners;
    }

    @Data
    @Schema(name = "OwnerCreateMultipleResponse", description = "Response after creating multiple Owner entities")
    public static class OwnerCreateMultipleResponse {
        @Schema(description = "List of created technical UUIDs")
        private List<UUID> ids;
    }

    @Data
    @Schema(name = "OwnerGetResponse", description = "Response containing Owner entity")
    public static class OwnerGetResponse {
        @Schema(description = "Owner entity JSON")
        private ObjectNode owner;
    }

    @Data
    @Schema(name = "OwnerListResponse", description = "Response containing list of Owners")
    public static class OwnerListResponse {
        @Schema(description = "Array of Owner entities")
        private ArrayNode owners;
    }

    @Data
    @Schema(name = "OwnerUpdateRequest", description = "Request to update an Owner entity")
    public static class OwnerUpdateRequest {
        @Schema(description = "Owner entity payload as JSON", required = true)
        private ObjectNode owner;
    }

    @Data
    @Schema(name = "OwnerUpdateResponse", description = "Response after updating an Owner entity")
    public static class OwnerUpdateResponse {
        @Schema(description = "Technical UUID of the updated entity")
        private UUID id;
    }

    @Data
    @Schema(name = "OwnerDeleteResponse", description = "Response after deleting an Owner entity")
    public static class OwnerDeleteResponse {
        @Schema(description = "Technical UUID of the deleted entity")
        private UUID id;
    }
}