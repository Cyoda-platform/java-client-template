package com.java_template.application.controller.owner.version_1;

import static com.java_template.common.config.Config.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.owner.version_1.Owner;
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

@RestController
@RequestMapping("/owners/v1")
@Tag(name = "Owner", description = "Owner entity proxy controller (v1)")
public class OwnerController {

    private static final Logger logger = LoggerFactory.getLogger(OwnerController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OwnerController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Owner", description = "Persist a single Owner entity and return technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createOwner(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Owner create request",
            content = @Content(schema = @Schema(implementation = OwnerRequest.class)))
            @RequestBody OwnerRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            Owner owner = new Owner();
            owner.setId(request.getId());
            owner.setName(request.getName());
            owner.setAddress(request.getAddress());
            owner.setPhone(request.getPhone());
            owner.setContactEmail(request.getContactEmail());
            owner.setPreferences(request.getPreferences());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                Owner.ENTITY_NAME,
                Owner.ENTITY_VERSION,
                owner
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(id.toString());
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createOwner: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createOwner", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating owner", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in createOwner", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple Owners", description = "Persist multiple Owner entities and return list of technicalIds")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createOwnersBatch(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "List of Owner create requests",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerRequest.class))))
            @RequestBody List<OwnerRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request list is required");
            List<Owner> owners = new ArrayList<>();
            for (OwnerRequest r : requests) {
                Owner owner = new Owner();
                owner.setId(r.getId());
                owner.setName(r.getName());
                owner.setAddress(r.getAddress());
                owner.setPhone(r.getPhone());
                owner.setContactEmail(r.getContactEmail());
                owner.setPreferences(r.getPreferences());
                owners.add(owner);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                Owner.ENTITY_NAME,
                Owner.ENTITY_VERSION,
                owners
            );
            List<UUID> ids = idsFuture.get();
            List<String> stringIds = new ArrayList<>();
            if (ids != null) {
                for (UUID u : ids) stringIds.add(u.toString());
            }
            return ResponseEntity.ok(stringIds);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createOwnersBatch: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createOwnersBatch", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating owners batch", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in createOwnersBatch", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Owner by technicalId", description = "Retrieve a single Owner by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OwnerResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getOwnerById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Owner not found");
            }
            JsonNode node = (JsonNode) dataPayload.getData();
            OwnerResponse response = objectMapper.treeToValue(node, OwnerResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getOwnerById: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getOwnerById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting owner", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getOwnerById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all Owners", description = "Retrieve all Owner entities")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllOwners() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                Owner.ENTITY_NAME,
                Owner.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<OwnerResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    OwnerResponse r = objectMapper.treeToValue(data, OwnerResponse.class);
                    responses.add(r);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllOwners", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting all owners", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getAllOwners", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Owner", description = "Update an Owner entity by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Owner update request",
                content = @Content(schema = @Schema(implementation = OwnerRequest.class)))
            @RequestBody OwnerRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Owner owner = new Owner();
            owner.setId(request.getId());
            owner.setName(request.getName());
            owner.setAddress(request.getAddress());
            owner.setPhone(request.getPhone());
            owner.setContactEmail(request.getContactEmail());
            owner.setPreferences(request.getPreferences());

            CompletableFuture<java.util.UUID> updatedId = entityService.updateItem(UUID.fromString(technicalId), owner);
            UUID id = updatedId.get();
            return ResponseEntity.ok(id.toString());
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateOwner: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateOwner", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating owner", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in updateOwner", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Owner", description = "Delete an Owner entity by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<UUID> deletedId = entityService.deleteItem(UUID.fromString(technicalId));
            UUID id = deletedId.get();
            return ResponseEntity.ok(id.toString());
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteOwner: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteOwner", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting owner", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in deleteOwner", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search Owners by field equals", description = "Simple in-memory search using a single EQUALS condition")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/search")
    public ResponseEntity<?> searchOwners(
            @Parameter(description = "JSON path of the field to search, e.g. $.contactEmail") @RequestParam("field") String field,
            @Parameter(description = "Operator, e.g. EQUALS") @RequestParam(value = "op", required = false, defaultValue = "EQUALS") String op,
            @Parameter(description = "Value to compare") @RequestParam("value") String value) {
        try {
            if (field == null || field.isBlank()) throw new IllegalArgumentException("field is required");
            if (value == null) throw new IllegalArgumentException("value is required");

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of(field, op, value)
            );

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                Owner.ENTITY_NAME,
                Owner.ENTITY_VERSION,
                condition,
                true
            );
            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            List<OwnerResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    OwnerResponse r = objectMapper.treeToValue(data, OwnerResponse.class);
                    responses.add(r);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for searchOwners: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchOwners", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching owners", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in searchOwners", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Static DTO classes for requests/responses

    @Data
    @Schema(name = "OwnerRequest", description = "Owner create/update request payload")
    public static class OwnerRequest {
        @Schema(description = "Technical id (optional on create)", example = "a3f1e9c2-...") private String id;
        @Schema(description = "Owner full name", example = "Ava Smith") private String name;
        @Schema(description = "Postal address", example = "123 Main St") private String address;
        @Schema(description = "Contact phone", example = "+1-555-5555") private String phone;
        @Schema(description = "Contact email", example = "ava@example.com") private String contactEmail;
        @Schema(description = "Preferences JSON string", example = "{\"species\":\"cat\",\"ageMax\":3}") private String preferences;
    }

    @Data
    @Schema(name = "OwnerResponse", description = "Owner response payload")
    public static class OwnerResponse {
        @Schema(description = "Technical id", example = "a3f1e9c2-...") private String id;
        @Schema(description = "Owner full name", example = "Ava Smith") private String name;
        @Schema(description = "Postal address", example = "123 Main St") private String address;
        @Schema(description = "Contact phone", example = "+1-555-5555") private String phone;
        @Schema(description = "Contact email", example = "ava@example.com") private String contactEmail;
        @Schema(description = "Preferences JSON string", example = "{\"species\":\"cat\",\"ageMax\":3}") private String preferences;
    }
}