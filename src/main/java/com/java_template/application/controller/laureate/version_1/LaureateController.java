package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.Data;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/laureates/v1")
@Tag(name = "Laureate", description = "Operations for Laureate entity (version 1)")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public LaureateController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a Laureate by its technical UUID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(value = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> getLaureateById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null || dataPayload.getData().isNull()) {
                throw new NoSuchElementException("Laureate not found for id: " + technicalId);
            }
            JsonNode dataNode = dataPayload.getData();
            LaureateResponse response = objectMapper.treeToValue(dataNode, LaureateResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getLaureateById: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLaureateById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (NoSuchElementException nse) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(nse.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getLaureateById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all Laureates", description = "Retrieve all Laureates (paged retrieval not implemented; returns all available)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(produces = "application/json")
    public ResponseEntity<?> getAllLaureates() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<LaureateResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload != null && payload.getData() != null && !payload.getData().isNull()) {
                        LaureateResponse resp = objectMapper.treeToValue(payload.getData(), LaureateResponse.class);
                        responses.add(resp);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in getAllLaureates", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in getAllLaureates", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search Laureates by simple condition", description = "Search Laureates using a simple single-field condition. Use query params: field, operator, value")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(value = "/search", produces = "application/json")
    public ResponseEntity<?> searchLaureates(
            @Parameter(description = "Field name (json path without $., e.g. id or category)") @RequestParam String field,
            @Parameter(description = "Operator (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.)") @RequestParam String operator,
            @Parameter(description = "Value to compare") @RequestParam String value
    ) {
        try {
            if (field == null || field.isBlank() || operator == null || operator.isBlank()) {
                throw new IllegalArgumentException("field and operator must be provided");
            }
            String jsonPath = "$." + field;
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of(jsonPath, operator, value)
            );
            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    condition,
                    true
            );
            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            List<LaureateResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload != null && payload.getData() != null && !payload.getData().isNull()) {
                        LaureateResponse resp = objectMapper.treeToValue(payload.getData(), LaureateResponse.class);
                        responses.add(resp);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in searchLaureates", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in searchLaureates", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Add a Laureate", description = "Create a new Laureate entity")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> addLaureate(
            @RequestBody(description = "Laureate to add") @org.springframework.web.bind.annotation.RequestBody LaureateRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body must not be null");
            }
            Laureate entity = objectMapper.convertValue(request, Laureate.class);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    entity
            );
            UUID id = idFuture.get();
            AddResponse resp = new AddResponse();
            resp.setTechnicalId(id != null ? id.toString() : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid addLaureate request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in addLaureate", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in addLaureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Add multiple Laureates", description = "Create multiple Laureate entities in a single request")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BulkAddResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping(value = "/bulk", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> addLaureates(
            @RequestBody(description = "List of laureates to add") @org.springframework.web.bind.annotation.RequestBody List<LaureateRequest> requests
    ) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body must contain at least one laureate");
            }
            List<Laureate> entities = new ArrayList<>();
            for (LaureateRequest r : requests) {
                entities.add(objectMapper.convertValue(r, Laureate.class));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();
            BulkAddResponse resp = new BulkAddResponse();
            List<String> technicalIds = new ArrayList<>();
            if (ids != null) {
                for (UUID u : ids) {
                    technicalIds.add(u != null ? u.toString() : null);
                }
            }
            resp.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid addLaureates request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in addLaureates", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in addLaureates", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update a Laureate", description = "Update an existing Laureate by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping(value = "/{technicalId}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> updateLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
            @RequestBody(description = "Laureate update payload") @org.springframework.web.bind.annotation.RequestBody LaureateRequest request
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body must not be null");
            }
            Laureate entity = objectMapper.convertValue(request, Laureate.class);
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(UUID.fromString(technicalId), entity);
            UUID updatedId = updatedFuture.get();
            UpdateResponse resp = new UpdateResponse();
            resp.setTechnicalId(updatedId != null ? updatedId.toString() : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid updateLaureate request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateLaureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in updateLaureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete a Laureate", description = "Delete an existing Laureate by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping(value = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> deleteLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deletedFuture.get();
            DeleteResponse resp = new DeleteResponse();
            resp.setTechnicalId(deletedId != null ? deletedId.toString() : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid deleteLaureate request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteLaureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in deleteLaureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Request DTOs

    @Data
    @Schema(name = "LaureateRequest", description = "Laureate request payload for create/update")
    public static class LaureateRequest {
        @Schema(description = "Source id", example = "853")
        private Integer id;

        @Schema(description = "First name", example = "Akira")
        private String firstname;

        @Schema(description = "Surname", example = "Suzuki")
        private String surname;

        @Schema(description = "Award year", example = "2010")
        private String year;

        @Schema(description = "Category", example = "Chemistry")
        private String category;

        @Schema(description = "Age at award (enriched)", example = "80")
        private Integer ageAtAward;

        @Schema(description = "Normalized country code (enriched)", example = "JP")
        private String normalizedCountryCode;

        @Schema(description = "Last updated timestamp", example = "2025-08-01T12:00:00Z")
        private String lastUpdatedAt;

        // Additional fields may be present but are optional for request mapping
        private String born;
        private String bornCity;
        private String bornCountry;
        private String bornCountryCode;
        private String died;
        private String gender;
        private String motivation;
        private String normalizedCountryCodeFull;
        private String sourceSnapshot;
        private String affiliationName;
        private String affiliationCity;
        private String affiliationCountry;
        private String affiliation;
    }

    // Response DTOs

    @Data
    @Schema(name = "LaureateResponse", description = "Laureate response payload")
    public static class LaureateResponse {
        @Schema(description = "Source id", example = "853")
        private Integer id;

        @Schema(description = "First name", example = "Akira")
        private String firstname;

        @Schema(description = "Surname", example = "Suzuki")
        private String surname;

        @Schema(description = "Award year", example = "2010")
        private String year;

        @Schema(description = "Category", example = "Chemistry")
        private String category;

        @Schema(description = "Age at award (enriched)", example = "80")
        private Integer ageAtAward;

        @Schema(description = "Normalized country code (enriched)", example = "JP")
        private String normalizedCountryCode;

        @Schema(description = "Last updated timestamp", example = "2025-08-01T12:00:00Z")
        private String lastUpdatedAt;
    }

    @Data
    @Schema(name = "AddResponse", description = "Response returned after creating an entity")
    public static class AddResponse {
        @Schema(description = "Technical id (UUID) of created entity", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        private String technicalId;
    }

    @Data
    @Schema(name = "BulkAddResponse", description = "Response returned after creating multiple entities")
    public static class BulkAddResponse {
        @Schema(description = "List of technical ids (UUID) of created entities")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "UpdateResponse", description = "Response returned after updating an entity")
    public static class UpdateResponse {
        @Schema(description = "Technical id (UUID) of updated entity", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteResponse", description = "Response returned after deleting an entity")
    public static class DeleteResponse {
        @Schema(description = "Technical id (UUID) of deleted entity", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        private String technicalId;
    }
}