package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/pets")
@Tag(name = "Pet API", description = "API for Pet entity (version 1) - proxy controller to EntityService")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Pet", description = "Create a Pet entity. This controller proxies the request to the EntityService. Returns only technicalId.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPet(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet payload", required = true,
                    content = @Content(schema = @Schema(implementation = PetRequest.class)))
            @RequestBody PetRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Pet pet = toEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, pet);
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createPet: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Execution caused IllegalArgumentException: {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                logger.info("Entity not found during create operation (unexpected): {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating Pet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in createPet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Pet by technicalId", description = "Retrieve a Pet by its technicalId. Proxies to EntityService.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPetById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
            }
            JsonNode dataNode = dataPayload.getData();
            Pet pet = objectMapper.treeToValue(dataNode, Pet.class);
            PetResponse response = toResponse(technicalId, pet);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in getPetById: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.info("Pet not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Execution caused IllegalArgumentException: {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when getting Pet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in getPetById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all Pets", description = "Retrieve all Pet entities. Proxies to EntityService.getItems.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllPets() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, null, null, null);
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<PetResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload == null || payload.getData() == null) continue;
                    Pet pet = objectMapper.treeToValue(payload.getData(), Pet.class);
                    String technicalId = pet != null && pet.getId() != null ? pet.getId() : null;
                    responses.add(toResponse(technicalId, pet));
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            logger.error("ExecutionException when getting all Pets", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getAllPets", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Pet", description = "Update a Pet entity by technicalId. Proxies to EntityService.updateItem. Returns technicalId.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet payload", required = true,
                    content = @Content(schema = @Schema(implementation = PetRequest.class)))
            @RequestBody PetRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            Pet pet = toEntity(request);
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(UUID.fromString(technicalId), pet);
            UUID updatedId = updatedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in updatePet: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.info("Pet not found for update: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Execution caused IllegalArgumentException: {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when updating Pet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in updatePet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Pet", description = "Delete a Pet entity by technicalId. Proxies to EntityService.deleteItem. Returns technicalId.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal Server Error")
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
            return ResponseEntity.ok(new TechnicalIdResponse(deletedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in deletePet: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.info("Pet not found for delete: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Execution caused IllegalArgumentException: {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when deleting Pet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in deletePet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Helper mapping methods
    private Pet toEntity(PetRequest req) {
        Pet pet = new Pet();
        pet.setId(req.getId());
        pet.setName(req.getName());
        pet.setSpecies(req.getSpecies());
        pet.setBreed(req.getBreed());
        pet.setSex(req.getSex());
        pet.setSize(req.getSize());
        pet.setAge(req.getAge());
        pet.setBio(req.getBio());
        pet.setHealthNotes(req.getHealthNotes());
        pet.setSource(req.getSource());
        pet.setStatus(req.getStatus());
        pet.setImportedAt(req.getImportedAt());
        pet.setPhotos(req.getPhotos());
        pet.setTags(req.getTags());
        return pet;
    }

    private PetResponse toResponse(String technicalId, Pet pet) {
        if (pet == null) return null;
        PetResponse r = new PetResponse();
        r.setTechnicalId(technicalId);
        r.setId(pet.getId());
        r.setName(pet.getName());
        r.setSpecies(pet.getSpecies());
        r.setBreed(pet.getBreed());
        r.setSex(pet.getSex());
        r.setSize(pet.getSize());
        r.setAge(pet.getAge());
        r.setBio(pet.getBio());
        r.setHealthNotes(pet.getHealthNotes());
        r.setSource(pet.getSource());
        r.setStatus(pet.getStatus());
        r.setImportedAt(pet.getImportedAt());
        r.setPhotos(pet.getPhotos());
        r.setTags(pet.getTags());
        return r;
    }

    // Static DTOs for requests and responses (explicit getters/setters to avoid relying on Lombok)

    @Schema(name = "PetRequest", description = "Pet request payload")
    public static class PetRequest {
        @Schema(description = "Technical id (optional on create)", example = "petrec_555")
        private String id;
        @Schema(description = "Pet name", example = "Mr Whiskers")
        private String name;
        @Schema(description = "Species of the pet", example = "cat")
        private String species;
        @Schema(description = "Breed", example = "Tabby")
        private String breed;
        @Schema(description = "Sex (M/F/unknown)", example = "M")
        private String sex;
        @Schema(description = "Size (small/medium/large)", example = "small")
        private String size;
        @Schema(description = "Age or age_range", example = "2 years")
        private String age;
        @Schema(description = "Bio/description")
        private String bio;
        @Schema(description = "Health notes")
        private String healthNotes;
        @Schema(description = "Origin/source", example = "Petstore")
        private String source;
        @Schema(description = "Status (available/pending/adopted/archived)", example = "AVAILABLE")
        private String status;
        @Schema(description = "Imported timestamp ISO-8601", example = "2025-08-28T10:00:00Z")
        private String importedAt;
        @Schema(description = "Photo URLs")
        private List<String> photos;
        @Schema(description = "Tags")
        private List<String> tags;

        public PetRequest() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getSpecies() { return species; }
        public void setSpecies(String species) { this.species = species; }

        public String getBreed() { return breed; }
        public void setBreed(String breed) { this.breed = breed; }

        public String getSex() { return sex; }
        public void setSex(String sex) { this.sex = sex; }

        public String getSize() { return size; }
        public void setSize(String size) { this.size = size; }

        public String getAge() { return age; }
        public void setAge(String age) { this.age = age; }

        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }

        public String getHealthNotes() { return healthNotes; }
        public void setHealthNotes(String healthNotes) { this.healthNotes = healthNotes; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getImportedAt() { return importedAt; }
        public void setImportedAt(String importedAt) { this.importedAt = importedAt; }

        public List<String> getPhotos() { return photos; }
        public void setPhotos(List<String> photos) { this.photos = photos; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }

    @Schema(name = "PetResponse", description = "Pet response payload")
    public static class PetResponse {
        @Schema(description = "Technical id", example = "petrec_555")
        private String technicalId;
        @Schema(description = "Pet id (source id)", example = "42")
        private String id;
        @Schema(description = "Pet name", example = "Mr Whiskers")
        private String name;
        @Schema(description = "Species of the pet", example = "cat")
        private String species;
        @Schema(description = "Breed", example = "Tabby")
        private String breed;
        @Schema(description = "Sex (M/F/unknown)", example = "M")
        private String sex;
        @Schema(description = "Size (small/medium/large)", example = "small")
        private String size;
        @Schema(description = "Age or age_range", example = "2 years")
        private String age;
        @Schema(description = "Bio/description")
        private String bio;
        @Schema(description = "Health notes")
        private String healthNotes;
        @Schema(description = "Origin/source", example = "Petstore")
        private String source;
        @Schema(description = "Status (available/pending/adopted/archived)", example = "AVAILABLE")
        private String status;
        @Schema(description = "Imported timestamp ISO-8601", example = "2025-08-28T10:00:00Z")
        private String importedAt;
        @Schema(description = "Photo URLs")
        private List<String> photos;
        @Schema(description = "Tags")
        private List<String> tags;

        public PetResponse() {}

        public String getTechnicalId() { return technicalId; }
        public void setTechnicalId(String technicalId) { this.technicalId = technicalId; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getSpecies() { return species; }
        public void setSpecies(String species) { this.species = species; }

        public String getBreed() { return breed; }
        public void setBreed(String breed) { this.breed = breed; }

        public String getSex() { return sex; }
        public void setSex(String sex) { this.sex = sex; }

        public String getSize() { return size; }
        public void setSize(String size) { this.size = size; }

        public String getAge() { return age; }
        public void setAge(String age) { this.age = age; }

        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }

        public String getHealthNotes() { return healthNotes; }
        public void setHealthNotes(String healthNotes) { this.healthNotes = healthNotes; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getImportedAt() { return importedAt; }
        public void setImportedAt(String importedAt) { this.importedAt = importedAt; }

        public List<String> getPhotos() { return photos; }
        public void setPhotos(List<String> photos) { this.photos = photos; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }

    @Schema(name = "TechnicalIdResponse", description = "Response containing only technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id", example = "uuid-or-id-string")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }

        public String getTechnicalId() { return technicalId; }
        public void setTechnicalId(String technicalId) { this.technicalId = technicalId; }
    }
}