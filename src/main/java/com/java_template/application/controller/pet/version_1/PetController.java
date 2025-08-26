package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Dull proxy controller for Pet entity. All business logic is implemented in workflows/processors,
 * controller only proxies requests to EntityService.
 */
@RestController
@RequestMapping("/api/pets")
@Tag(name = "Pet", description = "API for Pet entity (version 1) - proxy to EntityService")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // Create single Pet
    @Operation(summary = "Create Pet", description = "Persist a Pet entity. Returns the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPet(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet payload")
                                       @RequestBody PetRequest petRequest) {
        try {
            ObjectNode node = objectMapper.convertValue(petRequest, ObjectNode.class);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    com.java_template.application.entity.pet.version_1.Pet.ENTITY_NAME,
                    String.valueOf(com.java_template.application.entity.pet.version_1.Pet.ENTITY_VERSION),
                    node
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid request for createPet", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createPet", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in createPet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in createPet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Create multiple Pets (bulk)
    @Operation(summary = "Create multiple Pets", description = "Persist multiple Pet entities. Returns the list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createPetsBulk(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of Pet payloads")
                                            @RequestBody List<PetRequest> petRequests) {
        try {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (PetRequest pr : petRequests) {
                arrayNode.add(objectMapper.convertValue(pr, ObjectNode.class));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    com.java_template.application.entity.pet.version_1.Pet.ENTITY_NAME,
                    String.valueOf(com.java_template.application.entity.pet.version_1.Pet.ENTITY_VERSION),
                    arrayNode
            );
            List<UUID> ids = idsFuture.get();
            List<TechnicalIdResponse> resp = new ArrayList<>();
            for (UUID id : ids) {
                TechnicalIdResponse t = new TechnicalIdResponse();
                t.setTechnicalId(id.toString());
                resp.add(t);
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid request for createPetsBulk", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createPetsBulk", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in createPetsBulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in createPetsBulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Get Pet by technicalId
    @Operation(summary = "Get Pet by technicalId", description = "Retrieve a Pet by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPetById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    com.java_template.application.entity.pet.version_1.Pet.ENTITY_NAME,
                    String.valueOf(com.java_template.application.entity.pet.version_1.Pet.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            PetResponse resp = objectMapper.convertValue(node, PetResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid technicalId in getPetById", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getPetById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getPetById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getPetById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Get all Pets
    @Operation(summary = "List all Pets", description = "Retrieve all Pet entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllPets() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    com.java_template.application.entity.pet.version_1.Pet.ENTITY_NAME,
                    String.valueOf(com.java_template.application.entity.pet.version_1.Pet.ENTITY_VERSION)
            );
            ArrayNode arrayNode = itemsFuture.get();
            List<PetResponse> resp = new ArrayList<>();
            for (int i = 0; i < arrayNode.size(); i++) {
                resp.add(objectMapper.convertValue(arrayNode.get(i), PetResponse.class));
            }
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllPets", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getAllPets", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getAllPets", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Filter Pets by condition (in-memory)
    @Operation(summary = "Filter Pets", description = "Retrieve Pet entities by a simple search condition (in-memory filter)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/filter")
    public ResponseEntity<?> filterPets(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request")
                                        @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    com.java_template.application.entity.pet.version_1.Pet.ENTITY_NAME,
                    String.valueOf(com.java_template.application.entity.pet.version_1.Pet.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arrayNode = filteredItemsFuture.get();
            List<PetResponse> resp = new ArrayList<>();
            for (int i = 0; i < arrayNode.size(); i++) {
                resp.add(objectMapper.convertValue(arrayNode.get(i), PetResponse.class));
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid filter in filterPets", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in filterPets", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in filterPets", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in filterPets", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Update Pet
    @Operation(summary = "Update Pet", description = "Update a Pet entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet payload for update")
            @RequestBody PetRequest petRequest) {
        try {
            UUID id = UUID.fromString(technicalId);
            ObjectNode node = objectMapper.convertValue(petRequest, ObjectNode.class);
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    com.java_template.application.entity.pet.version_1.Pet.ENTITY_NAME,
                    String.valueOf(com.java_template.application.entity.pet.version_1.Pet.ENTITY_VERSION),
                    id,
                    node
            );
            UUID updatedId = updatedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid request in updatePet", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updatePet", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in updatePet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in updatePet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Delete Pet
    @Operation(summary = "Delete Pet", description = "Delete a Pet entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    com.java_template.application.entity.pet.version_1.Pet.ENTITY_NAME,
                    String.valueOf(com.java_template.application.entity.pet.version_1.Pet.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid technicalId in deletePet", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deletePet", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in deletePet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in deletePet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Static DTOs for request/response payloads

    @Data
    @Schema(name = "PetRequest", description = "Request payload for creating/updating a Pet")
    public static class PetRequest {
        @Schema(description = "Business id from source", example = "pet-42")
        private String id;

        @Schema(description = "Pet name", example = "Buddy")
        private String name;

        @Schema(description = "Species (dog/cat/other)", example = "dog")
        private String species;

        @Schema(description = "Breed name", example = "Labrador")
        private String breed;

        @Schema(description = "Numeric age value", example = "2")
        private Double age_value;

        @Schema(description = "Age unit (years|months)", example = "years")
        private String age_unit;

        @Schema(description = "Sex (M|F)", example = "M")
        private String sex;

        @Schema(description = "Size (small|medium|large)", example = "medium")
        private String size;

        @Schema(description = "Temperament tags", example = "[\"calm\",\"active\"]")
        private List<String> temperament_tags;

        @Schema(description = "Photo URLs")
        private List<String> photos;

        @Schema(description = "Location object with city, postal, lat, lon")
        private Map<String, Object> location;

        @Schema(description = "Health status (vaccinated|needs_care)")
        private String health_status;

        @Schema(description = "Availability status (available|adopted|pending)")
        private String availability_status;

        @Schema(description = "Source timestamp (ISO datetime)")
        private String created_at;
    }

    @Data
    @Schema(name = "PetResponse", description = "Response payload for Pet retrieval")
    public static class PetResponse {
        @Schema(description = "Business id from source", example = "pet-42")
        private String id;

        @Schema(description = "Pet name", example = "Buddy")
        private String name;

        @Schema(description = "Species", example = "dog")
        private String species;

        @Schema(description = "Numeric age value", example = "2")
        private Double age_value;

        @Schema(description = "Age unit", example = "years")
        private String age_unit;

        @Schema(description = "Location object (city, lat, lon)")
        private Map<String, Object> location;

        @Schema(description = "Availability status", example = "available")
        private String availability_status;

        @Schema(description = "Other fields may be present in the entity")
        private Map<String, Object> additionalProperties;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }
}