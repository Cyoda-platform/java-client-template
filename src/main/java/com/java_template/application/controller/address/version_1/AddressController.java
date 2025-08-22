package com.java_template.application.controller.address.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.address.version_1.Address;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.ArrayList;
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
 * AddressController - proxy controller for Address entity operations.
 *
 * Responsibilities:
 *  - Accept HTTP requests
 *  - Validate basic request format
 *  - Delegate to EntityService
 *  - Return appropriate HTTP responses
 *  - Handle exceptions with ExecutionException unwrapping
 *
 * Note: No business logic implemented here; controller acts as proxy only.
 */
@Tag(name = "Address API", description = "API for managing Address entities (v1)")
@RestController
@RequestMapping("/api/address/v1")
public class AddressController {

    private static final Logger logger = LoggerFactory.getLogger(AddressController.class);

    private final EntityService entityService;

    public AddressController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Address", description = "Creates a single Address entity and returns its technical id.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createAddress(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Address to create",
            content = @Content(schema = @Schema(implementation = CreateAddressRequest.class))
        )
        @RequestBody CreateAddressRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is missing");
            }

            Address addr = mapToEntity(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                Address.ENTITY_NAME,
                String.valueOf(Address.ENTITY_VERSION),
                addr
            );

            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for creating address: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating address", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while creating address", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple Addresses", description = "Creates multiple Address entities and returns their technical ids.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BatchTechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createAddresses(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "List of addresses to create",
            content = @Content(schema = @Schema(implementation = CreateAddressRequest.class))
        )
        @RequestBody List<CreateAddressRequest> requests
    ) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body is missing or empty");
            }

            List<Address> entities = new ArrayList<>();
            for (CreateAddressRequest r : requests) {
                entities.add(mapToEntity(r));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                Address.ENTITY_NAME,
                String.valueOf(Address.ENTITY_VERSION),
                entities
            );

            List<UUID> uuids = idsFuture.get();
            List<String> techIds = new ArrayList<>();
            for (UUID u : uuids) {
                techIds.add(u.toString());
            }

            BatchTechnicalIdResponse resp = new BatchTechnicalIdResponse();
            resp.setTechnicalIds(techIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid batch create request for addresses: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating addresses", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while creating addresses", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Address by technicalId", description = "Retrieves a single Address entity by technical id.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddressGetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getAddress(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable String technicalId
    ) {
        try {
            UUID tech = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Address.ENTITY_NAME,
                String.valueOf(Address.ENTITY_VERSION),
                tech
            );

            ObjectNode node = itemFuture.get();

            AddressGetResponse resp = new AddressGetResponse();
            resp.setTechnicalId(technicalId);
            resp.setAddress(node);

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getAddress: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching address", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while fetching address", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all Addresses", description = "Retrieves all Address entities.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllAddresses() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Address.ENTITY_NAME,
                String.valueOf(Address.ENTITY_VERSION)
            );

            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching addresses", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while fetching addresses", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search Addresses by condition", description = "Searches addresses using provided condition (simple filtering).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchAddresses(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Search condition request",
            content = @Content(schema = @Schema(implementation = com.java_template.common.util.SearchConditionRequest.class))
        )
        @RequestBody com.java_template.common.util.SearchConditionRequest condition
    ) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition is missing");
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                Address.ENTITY_NAME,
                String.valueOf(Address.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode arr = filteredItemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request for addresses: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching addresses", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while searching addresses", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Address", description = "Updates an Address entity identified by technical id.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateAddress(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Address update payload",
            content = @Content(schema = @Schema(implementation = CreateAddressRequest.class))
        )
        @RequestBody CreateAddressRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is missing");
            }
            UUID tech = UUID.fromString(technicalId);
            Address addr = mapToEntity(request);

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                Address.ENTITY_NAME,
                String.valueOf(Address.ENTITY_VERSION),
                tech,
                addr
            );

            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid update request for address: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating address", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while updating address", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Address", description = "Deletes an Address entity identified by technical id.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteAddress(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable String technicalId
    ) {
        try {
            UUID tech = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                Address.ENTITY_NAME,
                String.valueOf(Address.ENTITY_VERSION),
                tech
            );

            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for deleteAddress: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting address", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting address", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Helper: map request DTO to Address entity (no business logic here, just field copy)
    private Address mapToEntity(CreateAddressRequest req) {
        Address a = new Address();
        // Try to set common address fields if present in entity
        try {
            a.setUserId(req.getUserId());
        } catch (Exception ignored) {}
        try {
            a.setLine1(req.getLine1());
        } catch (Exception ignored) {}
        try {
            a.setLine2(req.getLine2());
        } catch (Exception ignored) {}
        try {
            a.setCity(req.getCity());
        } catch (Exception ignored) {}
        try {
            a.setRegion(req.getRegion());
        } catch (Exception ignored) {}
        try {
            a.setPostalCode(req.getPostalCode());
        } catch (Exception ignored) {}
        try {
            a.setCountry(req.getCountry());
        } catch (Exception ignored) {}
        try {
            a.setIsDefault(req.getIsDefault());
        } catch (Exception ignored) {}
        try {
            a.setVerified(req.getVerified());
        } catch (Exception ignored) {}
        try {
            a.setStatus(req.getStatus());
        } catch (Exception ignored) {}
        return a;
    }

    // DTOs

    @Data
    public static class CreateAddressRequest {
        @Schema(description = "User id associated with address", example = "user-123")
        private String userId;

        @Schema(description = "Address line 1", example = "123 Main St")
        private String line1;

        @Schema(description = "Address line 2", example = "Apt 4B")
        private String line2;

        @Schema(description = "City", example = "New York")
        private String city;

        @Schema(description = "Region / State", example = "NY")
        private String region;

        @Schema(description = "Postal code", example = "10001")
        private String postalCode;

        @Schema(description = "Country", example = "US")
        private String country;

        @Schema(description = "Whether this address is default for the user", example = "false")
        private Boolean isDefault;

        @Schema(description = "Whether address is verified", example = "false")
        private Boolean verified;

        @Schema(description = "Status of the address", example = "Active")
        private String status;
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the persisted entity", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        private String technicalId;
    }

    @Data
    public static class BatchTechnicalIdResponse {
        @Schema(description = "List of technical ids", example = "[\"uuid1\",\"uuid2\"]")
        private List<String> technicalIds;
    }

    @Data
    public static class AddressGetResponse {
        @Schema(description = "Technical id of the entity", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        private String technicalId;

        @Schema(description = "Address payload as stored")
        private ObjectNode address;
    }
}