package com.java_template.application.controller.address.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.address.version_1.Address;
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
import jakarta.validation.Valid;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(path = "/api/address/v1", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.ALL_VALUE)
@Tag(name = "Address", description = "Address entity operations (v1)")
public class AddressController {

    private static final Logger logger = LoggerFactory.getLogger(AddressController.class);

    private final EntityService entityService;

    public AddressController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Address", description = "Create a single Address entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Address created",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(path = "", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addAddress(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Address payload", required = true,
                    content = @Content(schema = @Schema(implementation = ObjectNode.class)))
            @RequestBody ObjectNode request
    ) {
        try {
            // forward the raw ObjectNode to the service
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    request
            );

            UUID id = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to add Address: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while adding Address", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding Address", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while adding Address", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create Addresses (bulk)", description = "Create multiple Address entities in bulk")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Addresses created",
                    content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(path = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addAddressesBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of Address payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class))))
            @RequestBody ArrayNode request
    ) {
        try {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    request
            );

            List<UUID> ids = idsFuture.get();
            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            for (UUID id : ids) resp.getTechnicalIds().add(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid bulk add request for Addresses: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while bulk adding Addresses", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while bulk adding Addresses", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while bulk adding Addresses", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Address", description = "Retrieve a single Address by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Address found",
                    content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> getAddress(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    id
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId or request for getAddress: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving Address", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving Address", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving Address", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Addresses", description = "Retrieve all Address entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Addresses retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(path = "", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> getAddresses() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving Addresses", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving Addresses", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving Addresses", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search Addresses by condition", description = "Retrieve Address entities filtered by a simple condition group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Addresses retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchAddresses(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest conditionRequest
    ) {
        try {
            // forward the search condition; use inMemory=true as requested by functional requirements
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    conditionRequest,
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request for Addresses: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching Addresses", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching Addresses", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while searching Addresses", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Address", description = "Update an existing Address by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Address updated",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateAddress(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Address payload to update", required = true,
                    content = @Content(schema = @Schema(implementation = ObjectNode.class)))
            @RequestBody ObjectNode request
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    id,
                    request
            );
            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to update Address: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating Address", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating Address", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while updating Address", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Address", description = "Delete an Address by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Address deleted",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping(path = "/{technicalId}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> deleteAddress(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to delete Address: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting Address", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting Address", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting Address", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    // --- DTOs for request/response payloads ---

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical identifier of the entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical identifiers")
        private List<String> technicalIds = new ArrayList<>();
    }
}