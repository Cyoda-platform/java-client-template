package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Dull proxy controller for Pet entity. All business logic lives in workflows/processors.
 */
@RestController
@RequestMapping("/api/v1/pets")
@Tag(name = "Pet", description = "Pet entity proxy endpoints (version 1)")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;

    public PetController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Pet", description = "Create a Pet entity (proxy to EntityService.addItem).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pet created", content = @Content(schema = @Schema(implementation = CreatePetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<?> createPet(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreatePetRequest.class)))
            @RequestBody CreatePetRequest request) {
        try {
            if (request == null || request.getPet() == null) {
                throw new IllegalArgumentException("request.pet must be provided");
            }
            Pet pet = request.getPet();
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    pet
            );
            UUID id = idFuture.get();
            CreatePetResponse resp = new CreatePetResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createPet request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createPet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Pets", description = "Create multiple Pet entities (proxy to EntityService.addItems).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pets created", content = @Content(schema = @Schema(implementation = CreatePetsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createPetsBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of pets", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreatePetsRequest.class))))
            @RequestBody CreatePetsRequest request) {
        try {
            if (request == null || request.getPets() == null) {
                throw new IllegalArgumentException("request.pets must be provided");
            }
            List<Pet> pets = request.getPets();
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    pets
            );
            List<UUID> ids = idsFuture.get();
            CreatePetsResponse resp = new CreatePetsResponse();
            resp.setTechnicalIds(ids.stream().map(UUID::toString).toList());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createPetsBatch request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createPetsBatch", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createPetsBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Pet by technicalId", description = "Retrieve a Pet by technicalId (proxy to EntityService.getItem).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetGetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPetById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    uuid
            );
            ObjectNode node = itemFuture.get();
            PetGetResponse resp = new PetGetResponse();
            resp.setPet(node);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid getPetById request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getPetById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getPetById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Pets", description = "Retrieve all Pets (proxy to EntityService.getItems).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetGetResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<?> getAllPets() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllPets", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getAllPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Pets by condition", description = "Search Pets using a SearchConditionRequest (proxy to EntityService.getItemsByCondition).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetGetResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchPets(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchPetsRequest.class)))
            @RequestBody SearchPetsRequest request) {
        try {
            if (request == null || request.getCondition() == null) {
                throw new IllegalArgumentException("request.condition must be provided");
            }
            SearchConditionRequest condition = request.getCondition();
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid searchPets request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchPets", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in searchPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Pet", description = "Update a Pet entity (proxy to EntityService.updateItem).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pet updated", content = @Content(schema = @Schema(implementation = UpdatePetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet payload", required = true,
                    content = @Content(schema = @Schema(implementation = UpdatePetRequest.class)))
            @RequestBody UpdatePetRequest request) {
        try {
            if (request == null || request.getPet() == null) {
                throw new IllegalArgumentException("request.pet must be provided");
            }
            UUID uuid = UUID.fromString(technicalId);
            Pet pet = request.getPet();
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    uuid,
                    pet
            );
            UUID updatedId = updatedIdFuture.get();
            UpdatePetResponse resp = new UpdatePetResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid updatePet request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updatePet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in updatePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Pet", description = "Delete a Pet entity (proxy to EntityService.deleteItem).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pet deleted", content = @Content(schema = @Schema(implementation = DeletePetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    uuid
            );
            UUID deletedId = deletedIdFuture.get();
            DeletePetResponse resp = new DeletePetResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid deletePet request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deletePet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in deletePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTO classes for requests/responses

    @Data
    public static class CreatePetRequest {
        @Schema(description = "Pet entity to create", required = true, implementation = Pet.class)
        private Pet pet;
    }

    @Data
    public static class CreatePetResponse {
        @Schema(description = "Technical id of created entity")
        private String technicalId;
    }

    @Data
    public static class CreatePetsRequest {
        @Schema(description = "List of Pet entities to create", implementation = Pet.class)
        private List<Pet> pets;
    }

    @Data
    public static class CreatePetsResponse {
        @Schema(description = "List of technical ids for created entities")
        private List<String> technicalIds;
    }

    @Data
    public static class PetGetResponse {
        @Schema(description = "Pet entity representation (as JSON)", implementation = ObjectNode.class)
        private ObjectNode pet;
    }

    @Data
    public static class SearchPetsRequest {
        @Schema(description = "Search condition request", implementation = SearchConditionRequest.class)
        private SearchConditionRequest condition;
    }

    @Data
    public static class UpdatePetRequest {
        @Schema(description = "Pet entity to update", implementation = Pet.class)
        private Pet pet;
    }

    @Data
    public static class UpdatePetResponse {
        @Schema(description = "Technical id of updated entity")
        private String technicalId;
    }

    @Data
    public static class DeletePetResponse {
        @Schema(description = "Technical id of deleted entity")
        private String technicalId;
    }
}