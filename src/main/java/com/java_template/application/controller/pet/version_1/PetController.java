package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
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
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Dull proxy controller for Pet entity. All business logic lives in workflows/processors.
 */
@RestController
@RequestMapping("/pets")
@Tag(name = "Pet API", description = "Proxy endpoints for Pet entity (version 1)")
@RequiredArgsConstructor
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "Create Pet", description = "Create a Pet entity. Controller only proxies to EntityService. Returns technicalId (UUID as string).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet to create", required = true,
            content = @Content(schema = @Schema(implementation = PetRequest.class)))
    @PostMapping
    public ResponseEntity<String> createPet(@RequestBody PetRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Pet pet = toEntity(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    pet
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(technicalId.toString());
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createPet: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createPet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during createPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Pet", description = "Retrieve a Pet by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
    @GetMapping("/{technicalId}")
    public ResponseEntity<PetResponse> getPet(@PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank())
                throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            PetResponse response = objectMapper.treeToValue((JsonNode) dataPayload.getData(), PetResponse.class);

            // Try to extract technicalId from meta if present
            JsonNode meta = (JsonNode) dataPayload.getMeta();
            if (meta != null && meta.has("entityId")) {
                response.setTechnicalId(meta.get("entityId").asText());
            } else {
                response.setTechnicalId(technicalId);
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getPet: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException during getPet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during getPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "List Pets", description = "Retrieve multiple Pets. Optional pagination parameters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<List<PetResponse>> listPets(
            @RequestParam(name = "pageSize", required = false) Integer pageSize,
            @RequestParam(name = "pageNumber", required = false) Integer pageNumber
    ) {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    pageSize,
                    pageNumber,
                    null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<PetResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = (JsonNode) payload.getData();
                    PetResponse resp = objectMapper.treeToValue(data, PetResponse.class);
                    JsonNode meta = (JsonNode) payload.getMeta();
                    if (meta != null && meta.has("entityId")) {
                        resp.setTechnicalId(meta.get("entityId").asText());
                    }
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for listPets: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException during listPets", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during listPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Update Pet", description = "Update a Pet entity by technicalId. Controller proxies to EntityService.updateItem.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet update payload", required = true,
            content = @Content(schema = @Schema(implementation = PetRequest.class)))
    @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
    @PutMapping("/{technicalId}")
    public ResponseEntity<String> updatePet(@PathVariable("technicalId") String technicalId, @RequestBody PetRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank())
                throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Pet pet = toEntity(request);

            CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(technicalId), pet);
            UUID updatedId = updated.get();
            return ResponseEntity.ok(updatedId.toString());
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updatePet: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during updatePet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during updatePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete Pet", description = "Delete a Pet entity by technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<String> deletePet(@PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank())
                throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<UUID> deleted = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deleted.get();
            return ResponseEntity.ok(deletedId.toString());
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deletePet: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during deletePet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during deletePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper to map request DTO to entity
    private Pet toEntity(PetRequest req) {
        Pet pet = new Pet();
        pet.setId(req.getId());
        pet.setName(req.getName());
        pet.setSpecies(req.getSpecies());
        pet.setBreed(req.getBreed());
        pet.setAgeMonths(req.getAgeMonths());
        pet.setStatus(req.getStatus());
        if (req.getMetadata() != null) {
            Pet.Metadata md = new Pet.Metadata();
            md.setEnrichedAt(req.getMetadata().getEnrichedAt());
            md.setImages(req.getMetadata().getImages() != null ? req.getMetadata().getImages() : new ArrayList<>());
            md.setTags(req.getMetadata().getTags() != null ? req.getMetadata().getTags() : new ArrayList<>());
            pet.setMetadata(md);
        }
        return pet;
    }

    // DTOs

    @Data
    @Schema(name = "PetRequest", description = "Pet creation/update request payload")
    public static class PetRequest {
        @Schema(description = "Business id for the pet (e.g., pet-77)", example = "pet-77")
        private String id;

        @Schema(description = "Pet name", example = "Mittens")
        private String name;

        @Schema(description = "Species (cat, dog, etc.)", example = "cat")
        private String species;

        @Schema(description = "Breed description", example = "tabby")
        private String breed;

        @Schema(description = "Age in months", example = "14")
        private Integer ageMonths;

        @Schema(description = "Status (available, adopted, archived)", example = "AVAILABLE")
        private String status;

        @Schema(description = "Additional metadata")
        private MetadataDTO metadata;

        @Data
        @Schema(name = "PetMetadata", description = "Metadata for Pet")
        public static class MetadataDTO {
            @Schema(description = "ISO-8601 timestamp when enriched", example = "2025-08-01T12:00:10Z")
            private String enrichedAt;

            @Schema(description = "List of image URLs")
            private List<String> images;

            @Schema(description = "List of tags")
            private List<String> tags;
        }
    }

    @Data
    @Schema(name = "PetResponse", description = "Pet response payload including technicalId")
    public static class PetResponse {
        @Schema(description = "Technical ID (UUID) assigned by the system", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Business id for the pet (e.g., pet-77)", example = "pet-77")
        private String id;

        @Schema(description = "Pet name", example = "Mittens")
        private String name;

        @Schema(description = "Species (cat, dog, etc.)", example = "cat")
        private String species;

        @Schema(description = "Breed description", example = "tabby")
        private String breed;

        @Schema(description = "Age in months", example = "14")
        private Integer ageMonths;

        @Schema(description = "Status (available, adopted, archived)", example = "AVAILABLE")
        private String status;

        @Schema(description = "Additional metadata")
        private MetadataDTO metadata;

        @Data
        @Schema(name = "PetMetadataResponse", description = "Metadata for Pet response")
        public static class MetadataDTO {
            @Schema(description = "ISO-8601 timestamp when enriched", example = "2025-08-01T12:00:10Z")
            private String enrichedAt;

            @Schema(description = "List of image URLs")
            private List<String> images;

            @Schema(description = "List of tags")
            private List<String> tags;
        }
    }
}