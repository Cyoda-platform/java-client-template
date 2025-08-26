package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/pets/v1")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Pet", description = "Pet entity proxy API (version 1)")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PetController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Pet", description = "Create a new Pet entity. Returns the technicalId (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPet(@Valid @RequestBody CreatePetRequest request) {
        try {
            Pet pet = toPetEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    pet
            );
            UUID id = idFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createPet: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createPet", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted creating Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Operation interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error creating Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Pets", description = "Create multiple Pet entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createPetsBulk(@Valid @RequestBody List<CreatePetRequest> requests) {
        try {
            List<Pet> pets = new ArrayList<>();
            for (CreatePetRequest r : requests) {
                pets.add(toPetEntity(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    pets
            );
            List<UUID> ids = idsFuture.get();
            List<CreateResponse> responses = new ArrayList<>();
            for (UUID id : ids) {
                CreateResponse cr = new CreateResponse();
                cr.setTechnicalId(id.toString());
                responses.add(cr);
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createPetsBulk: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createPetsBulk", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted creating Pets bulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Operation interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error creating Pets bulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Pet by technicalId", description = "Retrieve a Pet entity by its technicalId.")
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
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            PetResponse resp = objectMapper.treeToValue(node, PetResponse.class);
            // Ensure technicalId is present in response
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument in getPetById: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getPetById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted getPetById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Operation interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error getPetById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Pets", description = "Retrieve all Pet entities (read-only listing).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllPets() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION)
            );
            ArrayNode arrayNode = itemsFuture.get();
            List<PetResponse> list = new ArrayList<>();
            for (JsonNode n : arrayNode) {
                PetResponse resp = objectMapper.treeToValue(n, PetResponse.class);
                // If technicalId exists in node, keep it; otherwise skip
                if (resp.getTechnicalId() == null) {
                    // attempt to read "technicalId" or "id" from node
                    if (n.has("technicalId")) {
                        resp.setTechnicalId(n.get("technicalId").asText());
                    }
                }
                list.add(resp);
            }
            return ResponseEntity.ok(list);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in getAllPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted getAllPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Operation interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error getAllPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Pet", description = "Update a Pet entity by technicalId. Returns the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @Valid @RequestBody CreatePetRequest request) {
        try {
            Pet pet = toPetEntity(request);
            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    pet
            );
            UUID id = updatedId.get();
            UpdateResponse resp = new UpdateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument in updatePet: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updatePet", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted updatePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Operation interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error updatePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Pet", description = "Delete a Pet entity by technicalId. Returns the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID id = deletedId.get();
            DeleteResponse resp = new DeleteResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument in deletePet: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deletePet", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted deletePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Operation interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error deletePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Helper to map request DTO to entity Pet (no business logic)
    private Pet toPetEntity(CreatePetRequest r) {
        if (r == null) throw new IllegalArgumentException("Request body is null");
        Pet pet = new Pet();
        pet.setId(r.getId());
        pet.setName(r.getName());
        pet.setSpecies(r.getSpecies());
        pet.setBreed(r.getBreed());
        pet.setAge(r.getAge());
        pet.setColor(r.getColor());
        pet.setStatus(r.getStatus());
        pet.setHealthNotes(r.getHealthNotes());
        pet.setAvatarUrl(r.getAvatarUrl());
        pet.setTags(r.getTags());
        if (r.getSourceMetadata() != null) {
            Pet.SourceMetadata sm = new Pet.SourceMetadata();
            sm.setPetstoreId(r.getSourceMetadata().getPetstoreId());
            sm.setRaw(r.getSourceMetadata().getRaw());
            pet.setSourceMetadata(sm);
        } else {
            pet.setSourceMetadata(null);
        }
        return pet;
    }

    // ----- DTOs -----

    @Data
    @Schema(name = "CreatePetRequest", description = "Payload to create or update a Pet")
    public static class CreatePetRequest {
        @Schema(description = "Technical id (string)", example = "pet_0001")
        private String id;

        @Schema(description = "Pet name", example = "Mittens")
        private String name;

        @Schema(description = "Species (cat/dog/etc)", example = "cat")
        private String species;

        @Schema(description = "Breed", example = "Siamese")
        private String breed;

        @Schema(description = "Age in years", example = "2")
        private Integer age;

        @Schema(description = "Color description", example = "seal point")
        private String color;

        @Schema(description = "Status (available/pending_adoption/adopted/archived)", example = "AVAILABLE")
        private String status;

        @Schema(description = "Health notes", example = "Vaccinated")
        private String healthNotes;

        @Schema(description = "Avatar image URL", example = "https://cdn.example/mittens.jpg")
        private String avatarUrl;

        @Schema(description = "Tags list", example = "[\"playful\",\"lap cat\"]")
        private List<String> tags;

        @Schema(description = "Source metadata for traceability")
        private SourceMetadataDTO sourceMetadata;

        @Data
        @Schema(name = "SourceMetadataDTO", description = "Source metadata for Pet")
        public static class SourceMetadataDTO {
            @Schema(description = "Original petstore id", example = "123")
            private String petstoreId;

            @Schema(description = "Raw metadata map")
            private Map<String, Object> raw;
        }
    }

    @Data
    @Schema(name = "PetResponse", description = "Pet response payload")
    public static class PetResponse {
        @Schema(description = "Technical id", example = "pet_0001")
        private String technicalId;

        @Schema(description = "Business id", example = "123")
        private String id;

        @Schema(description = "Pet name", example = "Mittens")
        private String name;

        @Schema(description = "Species", example = "cat")
        private String species;

        @Schema(description = "Breed", example = "Siamese")
        private String breed;

        @Schema(description = "Age in years", example = "2")
        private Integer age;

        @Schema(description = "Color description", example = "seal point")
        private String color;

        @Schema(description = "Status", example = "AVAILABLE")
        private String status;

        @Schema(description = "Health notes", example = "Vaccinated")
        private String healthNotes;

        @Schema(description = "Avatar image URL", example = "https://cdn.example/mittens.jpg")
        private String avatarUrl;

        @Schema(description = "Tags list", example = "[\"playful\",\"lap cat\"]")
        private List<String> tags;

        @Schema(description = "Source metadata")
        private SourceMetadataDTO sourceMetadata;

        @Data
        @Schema(name = "SourceMetadataDTOResp", description = "Source metadata for Pet")
        public static class SourceMetadataDTO {
            @Schema(description = "Original petstore id", example = "123")
            private String petstoreId;

            @Schema(description = "Raw metadata map")
            private Map<String, Object> raw;
        }
    }

    @Data
    @Schema(name = "CreateResponse", description = "Response containing technicalId after creation")
    public static class CreateResponse {
        @Schema(description = "Technical id", example = "0f8fad5b-d9cb-469f-a165-70867728950e")
        private String technicalId;
    }

    @Data
    @Schema(name = "UpdateResponse", description = "Response containing technicalId after update")
    public static class UpdateResponse {
        @Schema(description = "Technical id", example = "0f8fad5b-d9cb-469f-a165-70867728950e")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteResponse", description = "Response containing technicalId after delete")
    public static class DeleteResponse {
        @Schema(description = "Technical id", example = "0f8fad5b-d9cb-469f-a165-70867728950e")
        private String technicalId;
    }
}