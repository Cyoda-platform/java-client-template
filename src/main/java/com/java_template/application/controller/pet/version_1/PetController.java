package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dull proxy controller for Pet entity. All business logic lives in workflows/processors.
 */
@RestController
@RequestMapping("/api/v1/pets")
@Tag(name = "Pet", description = "Pet entity proxy controller (version 1)")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper mapper;

    public PetController(EntityService entityService, ObjectMapper mapper) {
        this.entityService = entityService;
        this.mapper = mapper;
    }

    @PostConstruct
    private void init() {
        logger.info("PetController initialized for entityModel={} version={}", Pet.ENTITY_NAME, Pet.ENTITY_VERSION);
    }

    @Operation(summary = "Create Pet", description = "Persist a new Pet entity (delegates to EntityService).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PostMapping
    public ResponseEntity<?> createPet(@RequestBody PetRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("request body is required");
            }
            ObjectNode node = mapper.valueToTree(request);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                node
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new IdResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createPet: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createPet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error in createPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Pets", description = "Persist multiple Pet entities (delegates to EntityService).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = IdResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createPetsBulk(@RequestBody List<PetRequest> requests) {
        try {
            if (requests == null) {
                throw new IllegalArgumentException("request body is required");
            }
            ArrayNode arrayNode = mapper.valueToTree(requests);
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                arrayNode
            );
            List<UUID> ids = idsFuture.get();
            List<IdResponse> resp = new ArrayList<>();
            for (UUID id : ids) {
                resp.add(new IdResponse(id.toString()));
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createPetsBulk: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createPetsBulk", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating pets bulk", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error in createPetsBulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Pet by technicalId", description = "Retrieve a Pet by technicalId (delegates to EntityService).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPet(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
            }
            PetResponse resp = mapper.treeToValue(node, PetResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getPet: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getPet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error in getPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Pets", description = "Retrieve all Pet entities (delegates to EntityService).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllPets() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<PetResponse> list = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    ObjectNode node = (ObjectNode) array.get(i);
                    PetResponse pr = mapper.treeToValue(node, PetResponse.class);
                    list.add(pr);
                }
            }
            return ResponseEntity.ok(list);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllPets", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching all pets", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error in getAllPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Pets by condition", description = "Search pets using a SearchConditionRequest (delegates to EntityService).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchPets(@RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("search condition is required");
            }
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode array = filteredItemsFuture.get();
            List<PetResponse> list = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    ObjectNode node = (ObjectNode) array.get(i);
                    PetResponse pr = mapper.treeToValue(node, PetResponse.class);
                    list.add(pr);
                }
            }
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for searchPets: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchPets", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching pets", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error in searchPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Pet", description = "Update an existing Pet by technicalId (delegates to EntityService).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePet(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable("technicalId") String technicalId,
        @RequestBody PetRequest request) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("request body is required");
            }
            ObjectNode node = mapper.valueToTree(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                UUID.fromString(technicalId),
                node
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updatePet: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updatePet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error in updatePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Pet", description = "Delete a Pet by technicalId (delegates to EntityService).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePet(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(deletedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deletePet: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deletePet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error in deletePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- DTOs used for request/response payloads ---

    @Data
    @Schema(name = "PetRequest", description = "Pet create/update request payload")
    public static class PetRequest {
        @Schema(description = "Business id from Petstore or internal", example = "pet-789")
        private String id;

        @Schema(description = "Pet name", example = "Mittens")
        private String name;

        @Schema(description = "Species (dog/cat/etc)", example = "cat")
        private String species;

        @Schema(description = "Breed description", example = "Tabby")
        private String breed;

        @Schema(description = "Age in years or months", example = "2")
        private Integer age;

        @Schema(description = "Gender (male/female/unknown)", example = "female")
        private String gender;

        @Schema(description = "Status (available/reserved/adopted)", example = "available")
        private String status;

        @Schema(description = "Free text description")
        private String description;

        @Schema(description = "Photos URLs")
        private List<String> photos;

        @Schema(description = "Source (Petstore/Manual)", example = "Petstore")
        private String source;
    }

    @Data
    @Schema(name = "PetResponse", description = "Pet response payload")
    public static class PetResponse {
        @Schema(description = "Business id from Petstore or internal", example = "pet-789")
        private String id;

        @Schema(description = "Pet name", example = "Mittens")
        private String name;

        @Schema(description = "Species (dog/cat/etc)", example = "cat")
        private String species;

        @Schema(description = "Breed description", example = "Tabby")
        private String breed;

        @Schema(description = "Age in years or months", example = "2")
        private Integer age;

        @Schema(description = "Gender (male/female/unknown)", example = "female")
        private String gender;

        @Schema(description = "Status (available/reserved/adopted)", example = "available")
        private String status;

        @Schema(description = "Free text description")
        private String description;

        @Schema(description = "Photos URLs")
        private List<String> photos;

        @Schema(description = "Source (Petstore/Manual)", example = "Petstore")
        private String source;
    }

    @Data
    @Schema(name = "IdResponse", description = "Technical id response")
    public static class IdResponse {
        @Schema(description = "Technical ID of the created/updated/deleted entity", example = "job-tech-789")
        private String technicalId;

        public IdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}