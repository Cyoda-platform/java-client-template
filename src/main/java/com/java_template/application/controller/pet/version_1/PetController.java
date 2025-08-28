package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.pet.version_1.Pet;
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
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/pets")
@Tag(name = "Pet", description = "Pet entity proxy endpoints (version 1)")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Pet", description = "Creates a Pet entity. Returns a technicalId for the stored entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createPet(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet create request", required = true,
                    content = @Content(schema = @Schema(implementation = PetCreateRequest.class)))
            @RequestBody PetCreateRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Pet pet = new Pet();
            pet.setId(request.getId());
            pet.setName(request.getName());
            pet.setSpecies(request.getSpecies());
            pet.setBreed(request.getBreed());
            pet.setAge(request.getAge());
            pet.setSex(request.getSex());
            pet.setDescription(request.getDescription());
            pet.setPhotos(request.getPhotos());
            pet.setHealthNotes(request.getHealthNotes());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    pet
            );
            UUID entityId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create Pet: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during create operation: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument in create operation: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException when creating Pet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating Pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error when creating Pet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Pet by technicalId", description = "Retrieves a Pet entity by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<PetResponse> getPetByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            JsonNode dataNode = (JsonNode) dataPayload.getData();
            Pet pet = objectMapper.treeToValue(dataNode, Pet.class);

            PetResponse resp = new PetResponse();
            resp.setTechnicalId(getTechnicalIdFromPayload(dataPayload));
            resp.setId(pet.getId());
            resp.setName(pet.getName());
            resp.setSpecies(pet.getSpecies());
            resp.setBreed(pet.getBreed());
            resp.setAge(pet.getAge());
            resp.setSex(pet.getSex());
            resp.setStatus(pet.getStatus());
            resp.setDescription(pet.getDescription());
            resp.setPhotos(pet.getPhotos());
            resp.setHealthNotes(pet.getHealthNotes());
            resp.setCreatedAt(pet.getCreatedAt());
            resp.setUpdatedAt(pet.getUpdatedAt());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getPetByTechnicalId: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Pet not found: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument when fetching Pet: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException when retrieving Pet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when retrieving Pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error when retrieving Pet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // helper to extract technicalId from DataPayload without using reflection
    private String getTechnicalIdFromPayload(DataPayload payload) {
        try {
            // DataPayload typically exposes getTechnicalId(); use it directly
            Object tech = payload.getTechnicalId();
            if (tech != null) {
                return tech.toString();
            }
        } catch (NoSuchMethodError | NoSuchFieldError e) {
            logger.debug("DataPayload does not expose technicalId in expected way: {}", e.getMessage());
        } catch (Throwable t) {
            logger.debug("Could not extract technicalId from DataPayload: {}", t.getMessage());
        }
        return null;
    }

    @Data
    @Schema(name = "PetCreateRequest", description = "Payload to create a Pet")
    public static class PetCreateRequest {
        @Schema(description = "Business id assigned by shelter", example = "PET-123")
        private String id;
        @Schema(description = "Pet name", example = "Whiskers")
        private String name;
        @Schema(description = "Species (e.g., cat, dog)", example = "cat")
        private String species;
        @Schema(description = "Breed description", example = "Tabby")
        private String breed;
        @Schema(description = "Age in years", example = "2")
        private Integer age;
        @Schema(description = "Sex (M/F/Unknown)", example = "F")
        private String sex;
        @Schema(description = "Short bio", example = "Playful kitten")
        private String description;
        @Schema(description = "Photo URLs")
        private List<String> photos;
        @Schema(description = "Health notes (vaccinations, conditions)")
        private List<String> healthNotes;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the stored entity", example = "tech-pet-0001")
        private String technicalId;
    }

    @Data
    @Schema(name = "PetResponse", description = "Pet response payload")
    public static class PetResponse {
        @Schema(description = "Technical id", example = "tech-pet-0001")
        private String technicalId;
        @Schema(description = "Business id assigned by shelter", example = "PET-123")
        private String id;
        @Schema(description = "Pet name", example = "Whiskers")
        private String name;
        @Schema(description = "Species", example = "cat")
        private String species;
        @Schema(description = "Breed", example = "Tabby")
        private String breed;
        @Schema(description = "Age in years", example = "2")
        private Integer age;
        @Schema(description = "Sex", example = "F")
        private String sex;
        @Schema(description = "Status", example = "available")
        private String status;
        @Schema(description = "Short bio", example = "Playful kitten")
        private String description;
        @Schema(description = "Photo URLs")
        private List<String> photos;
        @Schema(description = "Health notes")
        private List<String> healthNotes;
        @Schema(description = "Created at (ISO timestamp)", example = "2025-08-28T12:00:00Z")
        private String createdAt;
        @Schema(description = "Updated at (ISO timestamp)", example = "2025-08-28T12:00:00Z")
        private String updatedAt;
    }
}