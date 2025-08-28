package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/pets/v1")
@Tag(name = "Pet Controller", description = "APIs for Pet entity (version 1)")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Pet", description = "Persist a Pet and start the Pet workflow. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPet(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet request payload")
            @RequestBody PetRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Pet pet = new Pet();
            pet.setPetId(request.getPetId());
            pet.setName(request.getName());
            pet.setSpecies(request.getSpecies());
            pet.setBreed(request.getBreed());
            pet.setAge(request.getAge());
            pet.setHealthRecords(request.getHealthRecords());
            pet.setImages(request.getImages());
            pet.setSource(request.getSource());
            // other fields like status/createdAt/metadata are workflow-managed or optional here

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    pet
            );
            UUID entityId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createPet: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during createPet: {}", cause.getMessage(), cause);
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during createPet: {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createPet", ee);
                return ResponseEntity.status(500).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createPet", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error during createPet", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Pet by technicalId", description = "Retrieve a Pet by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null) {
                return ResponseEntity.status(404).body("Pet not found");
            }
            JsonNode dataNode = dataPayload.getData();
            if (dataNode == null || dataNode.isNull()) {
                return ResponseEntity.status(404).body("Pet data not found");
            }
            PetResponse response = objectMapper.treeToValue(dataNode, PetResponse.class);
            // extract technicalId from meta if present
            try {
                JsonNode metaEntityId = dataPayload.getMeta() != null ? dataPayload.getMeta().get("entityId") : null;
                if (metaEntityId != null && !metaEntityId.isNull()) {
                    response.setTechnicalId(metaEntityId.asText());
                } else {
                    response.setTechnicalId(technicalId);
                }
            } catch (Exception e) {
                response.setTechnicalId(technicalId);
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getPet: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Pet not found: {}", cause.getMessage(), cause);
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during getPet: {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getPet", ee);
                return ResponseEntity.status(500).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getPet", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error during getPet", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    // Request/Response DTOs

    @Data
    @Schema(name = "PetRequest", description = "Request payload to create a Pet")
    public static class PetRequest {
        @Schema(description = "External pet id (catalog id)", example = "ext-123")
        private String petId;

        @Schema(description = "Pet name", example = "Mittens")
        private String name;

        @Schema(description = "Species", example = "Cat")
        private String species;

        @Schema(description = "Breed", example = "Tabby")
        private String breed;

        @Schema(description = "Age in years", example = "2")
        private Integer age;

        @Schema(description = "Health records", example = "[\"vaccinated\"]")
        private java.util.List<String> healthRecords = new java.util.ArrayList<>();

        @Schema(description = "Image URLs", example = "[\"https://...\"]")
        private java.util.List<String> images = new java.util.ArrayList<>();

        @Schema(description = "Source", example = "PetstoreAPI")
        private String source;
    }

    @Data
    @Schema(name = "PetResponse", description = "Response payload for Pet retrieval")
    public static class PetResponse {
        @Schema(description = "Technical ID", example = "pet_abc123")
        private String technicalId;

        @Schema(description = "External pet id (catalog id)", example = "ext-123")
        private String petId;

        @Schema(description = "Pet name", example = "Mittens")
        private String name;

        @Schema(description = "Species", example = "Cat")
        private String species;

        @Schema(description = "Breed", example = "Tabby")
        private String breed;

        @Schema(description = "Age in years", example = "2")
        private Integer age;

        @Schema(description = "Status", example = "Available")
        private String status;

        @Schema(description = "Health records", example = "[\"vaccinated\"]")
        private java.util.List<String> healthRecords = new java.util.ArrayList<>();

        @Schema(description = "Image URLs", example = "[\"https://...\"]")
        private java.util.List<String> images = new java.util.ArrayList<>();

        @Schema(description = "Source", example = "PetstoreAPI")
        private String source;

        @Schema(description = "Created at (ISO-8601)", example = "2025-08-28T12:00:00Z")
        private String createdAt;

        @Schema(description = "Metadata (freeform)")
        private java.util.Map<String, Object> metadata = new java.util.HashMap<>();
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the persisted entity", example = "pet_abc123")
        private String technicalId;
    }
}