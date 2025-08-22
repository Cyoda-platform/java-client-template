package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
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
import java.util.concurrent.ExecutionException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for Pet entity (version 1).
 *
 * Responsibilities:
 * - Proxy requests to EntityService
 * - Validate simple inputs
 * - Handle ExecutionException unwrap and map causes to proper HTTP responses
 *
 * Note: No business logic implemented here — only forwarding to EntityService.
 */
@RestController
@RequestMapping("/api/v1/pet")
@Tag(name = "Pet", description = "Operations for Pet entity (v1)")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;

    public PetController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create a pet", description = "Add a single Pet entity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddPetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> addPet(@RequestBody AddPetRequest request) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request data must be provided");
            }

            var idFuture = entityService.addItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                request.getData()
            );

            UUID id = idFuture.get();
            AddPetResponse resp = new AddPetResponse();
            resp.setId(id);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to addPet: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in addPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple pets", description = "Add multiple Pet entities in bulk")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddPetsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> addPets(@RequestBody AddPetsRequest request) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request data must be provided");
            }

            var idsFuture = entityService.addItems(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                request.getData()
            );

            List<UUID> ids = idsFuture.get();
            AddPetsResponse resp = new AddPetsResponse();
            resp.setIds(ids);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to addPets: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in addPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get a pet by technicalId", description = "Retrieve a Pet entity by its technical UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetPetResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPet(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }

            var itemFuture = entityService.getItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );

            ObjectNode data = itemFuture.get();
            GetPetResponse resp = new GetPetResponse();
            resp.setData(data);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid getPet request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in getPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all pets", description = "Retrieve all Pet entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = GetPetsResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getPets() {
        try {
            var itemsFuture = entityService.getItems(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION)
            );

            ArrayNode data = itemsFuture.get();
            GetPetsResponse resp = new GetPetsResponse();
            resp.setData(data);
            return ResponseEntity.ok(resp);
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in getPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search pets by condition", description = "Retrieve Pet entities matching a search condition")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetPetsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchPets(@RequestBody SearchRequest request) {
        try {
            if (request == null || request.getCondition() == null) {
                throw new IllegalArgumentException("Search condition must be provided");
            }

            var filteredItemsFuture = entityService.getItemsByCondition(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                request.getCondition(),
                true
            );

            ArrayNode data = filteredItemsFuture.get();
            GetPetsResponse resp = new GetPetsResponse();
            resp.setData(data);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid searchPets request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in searchPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update a pet", description = "Update a Pet entity by its technical UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdatePetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePet(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
        @RequestBody UpdatePetRequest request
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request data must be provided");
            }

            var updatedFuture = entityService.updateItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                UUID.fromString(technicalId),
                request.getData()
            );

            UUID updatedId = updatedFuture.get();
            UpdatePetResponse resp = new UpdatePetResponse();
            resp.setId(updatedId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid updatePet request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in updatePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete a pet", description = "Delete a Pet entity by its technical UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeletePetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePet(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }

            var deletedFuture = entityService.deleteItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );

            UUID deletedId = deletedFuture.get();
            DeletePetResponse resp = new DeletePetResponse();
            resp.setId(deletedId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid deletePet request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in deletePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ResponseEntity<?> handleExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Bad request: {}", cause.getMessage());
            return ResponseEntity.badRequest().body(cause.getMessage());
        } else {
            logger.error("ExecutionException unwrapped to unexpected error", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "AddPetRequest", description = "Request to add a single Pet")
    public static class AddPetRequest {
        @Schema(description = "Pet payload as JSON object", required = true)
        private ObjectNode data;
    }

    @Data
    @Schema(name = "AddPetResponse", description = "Response after creating a Pet")
    public static class AddPetResponse {
        @Schema(description = "Technical id of created entity")
        private UUID id;
    }

    @Data
    @Schema(name = "AddPetsRequest", description = "Request to add multiple Pets")
    public static class AddPetsRequest {
        @Schema(description = "Array of Pet payloads", required = true)
        private ArrayNode data;
    }

    @Data
    @Schema(name = "AddPetsResponse", description = "Response after creating multiple Pets")
    public static class AddPetsResponse {
        @Schema(description = "List of created technical ids")
        private List<UUID> ids;
    }

    @Data
    @Schema(name = "GetPetResponse", description = "Response containing a Pet")
    public static class GetPetResponse {
        @Schema(description = "Pet payload as JSON object")
        private ObjectNode data;
    }

    @Data
    @Schema(name = "GetPetsResponse", description = "Response containing multiple Pets")
    public static class GetPetsResponse {
        @Schema(description = "Array of Pet payloads")
        private ArrayNode data;
    }

    @Data
    @Schema(name = "SearchRequest", description = "Search request using SearchConditionRequest")
    public static class SearchRequest {
        @Schema(description = "Search condition object", required = true)
        private SearchConditionRequest condition;
    }

    @Data
    @Schema(name = "UpdatePetRequest", description = "Request to update a Pet")
    public static class UpdatePetRequest {
        @Schema(description = "Pet payload as JSON object", required = true)
        private ObjectNode data;
    }

    @Data
    @Schema(name = "UpdatePetResponse", description = "Response after updating a Pet")
    public static class UpdatePetResponse {
        @Schema(description = "Technical id of updated entity")
        private UUID id;
    }

    @Data
    @Schema(name = "DeletePetResponse", description = "Response after deleting a Pet")
    public static class DeletePetResponse {
        @Schema(description = "Technical id of deleted entity")
        private UUID id;
    }
}