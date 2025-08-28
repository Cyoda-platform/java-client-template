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
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/pets")
@Tag(name = "Pet API", description = "Event-driven proxy endpoints for Pet entity")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Pet", description = "Persist a Pet entity and trigger its workflows. Returns only the technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreatePetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPet(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreatePetRequest.class)))
            @RequestBody CreatePetRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            // Map request DTO to entity (no business logic)
            Pet pet = new Pet();
            pet.setId(request.getId());
            pet.setName(request.getName());
            pet.setSpecies(request.getSpecies());
            pet.setBreed(request.getBreed());
            pet.setAge(request.getAge());
            pet.setGender(request.getGender());
            pet.setPhotos(request.getPhotos());
            pet.setDescription(request.getDescription());
            pet.setHealthNotes(request.getHealthNotes());
            pet.setLocation(request.getLocation());
            pet.setSource(request.getSource());
            pet.setStatus(request.getStatus()); // optional; workflows may override

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    pet
            );
            java.util.UUID technicalId = idFuture.get();
            CreatePetResponse resp = new CreatePetResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid create pet request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating pet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Pet by technicalId", description = "Retrieve stored Pet entity by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetPetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPetById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId path parameter is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
            }

            JsonNode node = (JsonNode) dataPayload.getData();
            Pet pet = objectMapper.treeToValue(node, Pet.class);

            GetPetResponse resp = new GetPetResponse();
            resp.setTechnicalId(pet != null ? pet.getTechnicalId() : technicalId);
            resp.setEntity(pet);

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid get pet request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving pet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Pets", description = "Retrieve all stored Pet entities")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = GetPetResponse.class)))),
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
            List<GetPetResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload == null || payload.getData() == null) continue;
                    JsonNode dataNode = (JsonNode) payload.getData();
                    Pet pet = objectMapper.treeToValue(dataNode, Pet.class);
                    GetPetResponse r = new GetPetResponse();
                    r.setTechnicalId(pet != null ? pet.getTechnicalId() : null);
                    r.setEntity(pet);
                    responses.add(r);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while listing pets", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while listing pets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTO classes for requests/responses

    @Data
    public static class CreatePetRequest {
        @Schema(description = "External/source id", example = "pet-source-123")
        private String id;

        @Schema(description = "Pet name", example = "Mittens", required = true)
        private String name;

        @Schema(description = "Species (dog/cat/etc.)", example = "cat", required = true)
        private String species;

        @Schema(description = "Breed description", example = "Tabby")
        private String breed;

        @Schema(description = "Age in years or months", example = "2")
        private Integer age;

        @Schema(description = "Gender (male/female/unknown)", example = "female")
        private String gender;

        @Schema(description = "Photos URLs")
        private List<String> photos;

        @Schema(description = "Short bio", example = "Playful cat")
        private String description;

        @Schema(description = "Health notes", example = "Vaccinated")
        private String healthNotes;

        @Schema(description = "Shelter location or city", example = "Shelter A")
        private String location;

        @Schema(description = "Origin/source", example = "PetstoreAPI")
        private String source;

        @Schema(description = "Status (optional, workflows may override)", example = "AVAILABLE")
        private String status;
    }

    @Data
    public static class CreatePetResponse {
        @Schema(description = "Technical ID assigned to the persisted entity", example = "pet-technical-12345")
        private String technicalId;
    }

    @Data
    public static class GetPetResponse {
        @Schema(description = "Technical ID of the entity", example = "pet-technical-12345")
        private String technicalId;

        @Schema(description = "Pet entity object")
        private Pet entity;
    }
}