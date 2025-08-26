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
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody;
import jakarta.validation.Valid;
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
 * Controller for Pet entity - version 1
 *
 * Responsibilities:
 * - Accept HTTP requests
 * - Validate basic request format
 * - Proxy calls to EntityService
 * - Return appropriate HTTP responses
 * - Handle exceptions including ExecutionException unwrapping
 */
@RestController
@RequestMapping("/api/v1/pet")
@Tag(name = "Pet", description = "API for Pet entity (version 1)")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;

    public PetController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Add a pet", description = "Adds a single Pet entity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddPetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> addPet(
        @SwaggerRequestBody(description = "Pet payload", required = true, content = @Content(schema = @Schema(implementation = AddPetRequest.class)))
        @Valid @RequestBody AddPetRequest request
    ) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request data is missing");
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                request.getData()
            );
            UUID id = idFuture.get();
            AddPetResponse resp = new AddPetResponse();
            resp.setId(id);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid addPet request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException while adding pet", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while adding pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Add multiple pets", description = "Adds multiple Pet entities in batch")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddPetsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> addPets(
        @SwaggerRequestBody(description = "List of Pet payloads", required = true, content = @Content(array = @ArraySchema(schema = @Schema(implementation = AddPetsRequest.class))))
        @Valid @RequestBody AddPetsRequest request
    ) {
        try {
            if (request == null || request.getData() == null || request.getData().isEmpty()) {
                throw new IllegalArgumentException("Request data is missing or empty");
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                request.getData()
            );
            List<UUID> ids = idsFuture.get();
            AddPetsResponse resp = new AddPetsResponse();
            resp.setIds(ids);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid addPets request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding pets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException while adding pets", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while adding pets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all pets", description = "Retrieves all Pet entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = GetPetsResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllPets() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            GetPetsResponse resp = new GetPetsResponse();
            resp.setItems(items);
            return ResponseEntity.ok(resp);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while retrieving pets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException while retrieving pets", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving pets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search pets by condition", description = "Retrieves Pet entities matching the given search condition (in-memory filtering)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = GetPetsResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchPets(
        @SwaggerRequestBody(description = "Search condition", required = true, content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
        @Valid @RequestBody SearchConditionRequest condition
    ) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition is required");
            }
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode items = filteredItemsFuture.get();
            GetPetsResponse resp = new GetPetsResponse();
            resp.setItems(items);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid searchPets request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while searching pets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException while searching pets", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while searching pets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get a pet by technicalId", description = "Retrieves a single Pet entity by its technical UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetPetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPetById(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode item = itemFuture.get();
            GetPetResponse resp = new GetPetResponse();
            resp.setData(item);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid getPetById request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while retrieving pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException while retrieving pet", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update a pet", description = "Updates a Pet entity by its technical UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdatePetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePet(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId,
        @SwaggerRequestBody(description = "Pet payload for update", required = true, content = @Content(schema = @Schema(implementation = UpdatePetRequest.class)))
        @Valid @RequestBody UpdatePetRequest request
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request data is missing");
            }
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                UUID.fromString(technicalId),
                request.getData()
            );
            UUID updatedId = updatedIdFuture.get();
            UpdatePetResponse resp = new UpdatePetResponse();
            resp.setId(updatedId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid updatePet request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while updating pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException while updating pet", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while updating pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete a pet", description = "Deletes a Pet entity by its technical UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeletePetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePet(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            UUID deletedId = deletedIdFuture.get();
            DeletePetResponse resp = new DeletePetResponse();
            resp.setId(deletedId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid deletePet request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while deleting pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException while deleting pet", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while deleting pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- DTOs for requests and responses ---

    @Data
    public static class AddPetRequest {
        @Schema(description = "Pet entity JSON payload", required = true, implementation = ObjectNode.class)
        private ObjectNode data;
    }

    @Data
    public static class AddPetResponse {
        @Schema(description = "Created technical id", required = true)
        private UUID id;
    }

    @Data
    public static class AddPetsRequest {
        @Schema(description = "List of Pet JSON payloads", required = true, implementation = ArrayNode.class)
        private List<ObjectNode> data;
    }

    @Data
    public static class AddPetsResponse {
        @Schema(description = "List of created technical ids", required = true)
        private List<UUID> ids;
    }

    @Data
    public static class GetPetResponse {
        @Schema(description = "Pet entity JSON payload", implementation = ObjectNode.class)
        private ObjectNode data;
    }

    @Data
    public static class GetPetsResponse {
        @Schema(description = "Array of Pet entities", implementation = ArrayNode.class)
        private ArrayNode items;
    }

    @Data
    public static class UpdatePetRequest {
        @Schema(description = "Pet entity JSON payload for update", implementation = ObjectNode.class)
        private ObjectNode data;
    }

    @Data
    public static class UpdatePetResponse {
        @Schema(description = "Updated technical id", required = true)
        private UUID id;
    }

    @Data
    public static class DeletePetResponse {
        @Schema(description = "Deleted technical id", required = true)
        private UUID id;
    }
}