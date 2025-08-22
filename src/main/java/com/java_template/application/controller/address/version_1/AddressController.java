package com.java_template.application.controller.address.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.address.version_1.Address;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Dull proxy controller for Address entity. All business logic occurs in workflows/processors.
 */
@RestController
@RequestMapping("/api/address/v1/addresses")
@Tag(name = "Address Controller", description = "Proxy API for Address entity (v1)")
public class AddressController {

    private static final Logger logger = LoggerFactory.getLogger(AddressController.class);

    private final EntityService entityService;

    public AddressController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Address", description = "Persist an Address entity. Returns only technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createAddress(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Address payload",
                    content = @Content(schema = @Schema(implementation = AddressRequest.class))
            )
            @Valid @RequestBody AddressRequest request
    ) {
        try {
            // pass request payload as entity to EntityService (controller must be dull)
            UUID technicalId = entityService.addItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    request
            ).get();

            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createAddress", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createAddress", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createAddress", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Addresses", description = "Persist multiple Address entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createAddressesBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Batch of Address payloads",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AddressRequest.class)))
            )
            @Valid @RequestBody List<AddressRequest> requests
    ) {
        try {
            List<UUID> ids = entityService.addItems(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    requests
            ).get();

            List<String> technicalIds = ids.stream().map(UUID::toString).collect(Collectors.toList());
            return ResponseEntity.ok(new TechnicalIdsResponse(technicalIds));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createAddressesBatch", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createAddressesBatch", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createAddressesBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Address by technicalId", description = "Retrieve Address entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddressGetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getAddressById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            ObjectNode entity = entityService.getItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();

            AddressGetResponse response = new AddressGetResponse(technicalId, entity);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getAddressById: {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAddressById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getAddressById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Addresses", description = "Retrieve all Address entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllAddresses() {
        try {
            ArrayNode items = entityService.getItems(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION)
            ).get();

            return ResponseEntity.ok(items);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllAddresses", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getAllAddresses", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Addresses by condition", description = "Retrieve Address entities matching provided search condition.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchAddresses(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Search condition",
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class))
            )
            @Valid @RequestBody SearchConditionRequest condition
    ) {
        try {
            ArrayNode items = entityService.getItemsByCondition(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    condition,
                    true
            ).get();

            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search condition", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchAddresses", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in searchAddresses", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Address", description = "Update an existing Address entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateAddress(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Address payload for update",
                    content = @Content(schema = @Schema(implementation = AddressRequest.class))
            )
            @Valid @RequestBody AddressRequest request
    ) {
        try {
            UUID updatedId = entityService.updateItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    request
            ).get();

            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateAddress: {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateAddress", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in updateAddress", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Address", description = "Delete an Address entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteAddress(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID deletedId = entityService.deleteItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();

            return ResponseEntity.ok(new TechnicalIdResponse(deletedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for deleteAddress: {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteAddress", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in deleteAddress", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- Static DTO classes required by the controller (requests/responses) ---

    @Data
    @Schema(name = "AddressRequest", description = "Request payload for creating/updating an Address")
    public static class AddressRequest {
        @Schema(description = "Business id of the address", example = "addr-1")
        private String id;
        @Schema(description = "User business id", example = "user-1")
        private String userId;
        @Schema(description = "Address line 1", example = "123 Main St")
        private String line1;
        @Schema(description = "Address line 2", example = "Apt 4B")
        private String line2;
        @Schema(description = "City", example = "Townsville")
        private String city;
        @Schema(description = "Postal code", example = "12345")
        private String postalCode;
        @Schema(description = "Region", example = "Region")
        private String region;
        @Schema(description = "Country (ISO)", example = "US")
        private String country;
        @Schema(description = "Phone", example = "555-0100")
        private String phone;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id", example = "tx-addr-001")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing multiple technical ids")
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical ids")
        private List<String> technicalIds;

        public TechnicalIdsResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "AddressGetResponse", description = "Response containing technicalId and the entity")
    public static class AddressGetResponse {
        @Schema(description = "Technical id", example = "tx-addr-001")
        private String technicalId;

        @Schema(description = "Raw entity JSON")
        private ObjectNode entity;

        public AddressGetResponse(String technicalId, ObjectNode entity) {
            this.technicalId = technicalId;
            this.entity = entity;
        }
    }
}