package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
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
 * Controller responsible for CRUD operations on Pet entity.
 * This controller acts only as a proxy to EntityService and does not implement business logic.
 */
@RestController
@RequestMapping(path = "/api/v1/pet", produces = "application/json")
@Tag(name = "Pet", description = "Operations related to Pet entity")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;

    public PetController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create a pet", description = "Add a single Pet entity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetCreateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = "application/json")
    public ResponseEntity<?> addPet(@RequestBody PetCreateRequest request) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request data must not be null");
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                request.getData()
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new PetCreateResponse(id));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for addPet: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (Exception e) {
            logger.error("Unexpected error in addPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple pets", description = "Add multiple Pet entities in bulk")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetBulkCreateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/bulk", consumes = "application/json")
    public ResponseEntity<?> addPets(@RequestBody PetBulkCreateRequest request) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request data must not be null");
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                request.getData()
            );
            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new PetBulkCreateResponse(ids));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for addPets: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (Exception e) {
            logger.error("Unexpected error in addPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get a pet by technical ID", description = "Retrieve a single Pet entity by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetGetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}")
    public ResponseEntity<?> getPet(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                id
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(new PetGetResponse(node));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getPet: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (Exception e) {
            logger.error("Unexpected error in getPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List all pets", description = "Retrieve all Pet entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetListResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listPets() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(new PetListResponse(items));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (Exception e) {
            logger.error("Unexpected error in listPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search pets by simple condition", description = "Search Pet entities by a single field condition")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetListResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/search", consumes = "application/json")
    public ResponseEntity<?> searchPets(@RequestBody PetSearchRequest request) {
        try {
            if (request == null || request.getFieldName() == null || request.getOperator() == null) {
                throw new IllegalArgumentException("fieldName and operator are required for search");
            }

            String jsonPath = "$." + request.getFieldName();
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of(jsonPath, request.getOperator(), request.getValue())
            );

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(new PetListResponse(items));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for searchPets: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (Exception e) {
            logger.error("Unexpected error in searchPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update a pet", description = "Update an existing Pet entity by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetUpdateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = "application/json")
    public ResponseEntity<?> updatePet(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable String technicalId,
        @RequestBody PetUpdateRequest request
    ) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request data must not be null");
            }
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                id,
                request.getData()
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new PetUpdateResponse(updatedId));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updatePet: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (Exception e) {
            logger.error("Unexpected error in updatePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete a pet", description = "Delete a Pet entity by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetDeleteResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}")
    public ResponseEntity<?> deletePet(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                id
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new PetDeleteResponse(deletedId));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for deletePet: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (Exception e) {
            logger.error("Unexpected error in deletePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ResponseEntity<?> handleExecutionException(Throwable cause) {
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument in async operation: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
        } else {
            logger.error("Unexpected execution error", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : "Unknown error");
        }
    }

    // DTOs

    @Data
    @Schema(name = "PetCreateRequest", description = "Request to create a Pet")
    public static class PetCreateRequest {
        @Schema(description = "Pet data as JSON object", required = true)
        private ObjectNode data;
    }

    @Data
    @Schema(name = "PetCreateResponse", description = "Response after creating a Pet")
    public static class PetCreateResponse {
        @Schema(description = "Technical id of created entity")
        private UUID technicalId;

        public PetCreateResponse(UUID technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "PetBulkCreateRequest", description = "Request to create multiple Pets")
    public static class PetBulkCreateRequest {
        @Schema(description = "List of Pet JSON objects", required = true)
        private List<ObjectNode> data;
    }

    @Data
    @Schema(name = "PetBulkCreateResponse", description = "Response after creating multiple Pets")
    public static class PetBulkCreateResponse {
        @Schema(description = "List of technical ids of created entities")
        private List<UUID> technicalIds;

        public PetBulkCreateResponse(List<UUID> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "PetGetResponse", description = "Response containing a Pet")
    public static class PetGetResponse {
        @Schema(description = "Pet data as JSON object")
        private ObjectNode data;

        public PetGetResponse(ObjectNode data) {
            this.data = data;
        }
    }

    @Data
    @Schema(name = "PetListResponse", description = "Response containing a list of Pets")
    public static class PetListResponse {
        @Schema(description = "Array of Pet JSON objects")
        private ArrayNode items;

        public PetListResponse(ArrayNode items) {
            this.items = items;
        }
    }

    @Data
    @Schema(name = "PetSearchRequest", description = "Request to search Pets by a simple condition")
    public static class PetSearchRequest {
        @Schema(description = "Field name to search on (without JSON path prefix)", required = true, example = "name")
        private String fieldName;

        @Schema(description = "Operator for comparison (EQUALS, NOT_EQUAL, IEQUALS, etc.)", required = true, example = "EQUALS")
        private String operator;

        @Schema(description = "Value to compare against", required = false, example = "Fido")
        private String value;
    }

    @Data
    @Schema(name = "PetUpdateRequest", description = "Request to update a Pet")
    public static class PetUpdateRequest {
        @Schema(description = "Pet data as JSON object", required = true)
        private ObjectNode data;
    }

    @Data
    @Schema(name = "PetUpdateResponse", description = "Response after updating a Pet")
    public static class PetUpdateResponse {
        @Schema(description = "Technical id of updated entity")
        private UUID technicalId;

        public PetUpdateResponse(UUID technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "PetDeleteResponse", description = "Response after deleting a Pet")
    public static class PetDeleteResponse {
        @Schema(description = "Technical id of deleted entity")
        private UUID technicalId;

        public PetDeleteResponse(UUID technicalId) {
            this.technicalId = technicalId;
        }
    }
}