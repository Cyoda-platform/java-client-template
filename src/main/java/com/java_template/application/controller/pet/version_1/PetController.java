package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/pets")
@Tag(name = "Pet Controller", description = "Operations for Pet entity (proxy to EntityService)")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Pet", description = "Create a Pet entity. Returns technicalId of created entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPet(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet create request", required = true,
                    content = @Content(schema = @Schema(implementation = PetRequest.class)))
            @RequestBody PetRequest petRequest) {
        try {
            if (petRequest == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Pet pet = new Pet();
            pet.setId(petRequest.getId());
            pet.setName(petRequest.getName());
            pet.setBreed(petRequest.getBreed());
            pet.setAge(petRequest.getAge());
            pet.setDescription(petRequest.getDescription());
            pet.setSource(petRequest.getSource());
            pet.setStatus(petRequest.getStatus());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    pet
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new OperationResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create pet: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating pet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unhandled exception while creating pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Pet", description = "Retrieve a Pet by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null || dataPayload.getData().isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
            }
            PetResponse response = objectMapper.treeToValue(dataPayload.getData(), PetResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid getPet request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving pet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unhandled exception while retrieving pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Pets", description = "Retrieve a list of Pet entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listPets() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<PetResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload != null && payload.getData() != null && !payload.getData().isNull()) {
                        PetResponse response = objectMapper.treeToValue(payload.getData(), PetResponse.class);
                        responses.add(response);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while listing pets", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unhandled exception while listing pets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Pet", description = "Update a Pet entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet update request", required = true,
                    content = @Content(schema = @Schema(implementation = PetRequest.class)))
            @RequestBody PetRequest petRequest) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (petRequest == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Pet pet = new Pet();
            pet.setId(petRequest.getId());
            pet.setName(petRequest.getName());
            pet.setBreed(petRequest.getBreed());
            pet.setAge(petRequest.getAge());
            pet.setDescription(petRequest.getDescription());
            pet.setSource(petRequest.getSource());
            pet.setStatus(petRequest.getStatus());

            CompletableFuture<UUID> updatedFuture = entityService.updateItem(UUID.fromString(technicalId), pet);
            UUID updatedId = updatedFuture.get();
            return ResponseEntity.ok(new OperationResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid updatePet request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating pet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unhandled exception while updating pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Pet", description = "Delete a Pet entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deletedFuture.get();
            return ResponseEntity.ok(new OperationResponse(deletedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid deletePet request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting pet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unhandled exception while deleting pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs used for requests/responses

    @Data
    @Schema(name = "PetRequest", description = "Request payload for creating/updating a Pet")
    public static class PetRequest {
        @Schema(description = "Business identifier for the pet (optional on create)", example = "pet-123")
        private String id;

        @Schema(description = "Pet name", example = "Whiskers", required = true)
        private String name;

        @Schema(description = "Breed or species", example = "Siamese")
        private String breed;

        @Schema(description = "Age in years", example = "2")
        private Integer age;

        @Schema(description = "Short description", example = "Playful kitten")
        private String description;

        @Schema(description = "Record source", example = "Petstore")
        private String source;

        @Schema(description = "Status of the pet", example = "AVAILABLE")
        private String status;
    }

    @Data
    @Schema(name = "PetResponse", description = "Response payload representing a Pet")
    public static class PetResponse {
        @Schema(description = "Business identifier for the pet", example = "pet-123")
        private String id;

        @Schema(description = "Pet name", example = "Whiskers")
        private String name;

        @Schema(description = "Breed or species", example = "Siamese")
        private String breed;

        @Schema(description = "Age in years", example = "2")
        private Integer age;

        @Schema(description = "Short description", example = "Playful kitten")
        private String description;

        @Schema(description = "Record source", example = "Petstore")
        private String source;

        @Schema(description = "Status of the pet", example = "AVAILABLE")
        private String status;
    }

    @Data
    @Schema(name = "OperationResponse", description = "Response containing technicalId for operations")
    public static class OperationResponse {
        @Schema(description = "Technical ID of the entity", example = "550e8400-e29b-41d4-a716-446655440000")
        private String technicalId;

        public OperationResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}