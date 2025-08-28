package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
@RequestMapping("/api/pet/v1")
@Tag(name = "Pet", description = "Pet entity proxy controller (version 1)")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PetController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get pet by technicalId", description = "Retrieve a Pet entity by its technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = PetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/pets/{technicalId}")
    public ResponseEntity<?> getPetById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
            }
            JsonNode data = dataPayload.getData();
            PetResponse response = objectMapper.treeToValue(data, PetResponse.class);

            // Attempt to extract technicalId from meta if present
            try {
                ObjectNode meta = (ObjectNode) dataPayload.getMeta();
                if (meta != null && meta.has("entityId")) {
                    response.setTechnicalId(meta.get("entityId").asText());
                } else {
                    response.setTechnicalId(technicalId);
                }
            } catch (Exception e) {
                response.setTechnicalId(technicalId);
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request in getPetById: {}", iae.getMessage());
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

    @Operation(summary = "List pets", description = "Retrieve list of Pet entities. Optional filter by species")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = PetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/pets")
    public ResponseEntity<?> listPets(
            @Parameter(name = "species", description = "Optional species filter (exact match)")
            @RequestParam(value = "species", required = false) String species) {
        try {
            List<DataPayload> dataPayloads;
            if (species != null && !species.isBlank()) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.species", "EQUALS", species));
                CompletableFuture<List<DataPayload>> filteredFuture =
                        entityService.getItemsByCondition(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, condition, true);
                dataPayloads = filteredFuture.get();
            } else {
                CompletableFuture<List<DataPayload>> itemsFuture =
                        entityService.getItems(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, null, null, null);
                dataPayloads = itemsFuture.get();
            }

            List<PetResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    PetResponse resp = objectMapper.treeToValue(data, PetResponse.class);
                    try {
                        ObjectNode meta = (ObjectNode) payload.getMeta();
                        if (meta != null && meta.has("entityId")) {
                            resp.setTechnicalId(meta.get("entityId").asText());
                        }
                    } catch (Exception ignored) {}
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request in listPets: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in listPets", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in listPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create a Pet", description = "Create a Pet entity (proxy to EntityService). Returns technicalId of created entity.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/pets")
    public ResponseEntity<?> createPet(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet to create")
            @RequestBody PetRequest petRequest) {
        try {
            if (petRequest == null) {
                throw new IllegalArgumentException("petRequest must be provided");
            }

            // Map request DTO to entity
            Pet entity = objectMapper.convertValue(petRequest, Pet.class);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    entity
            );
            UUID createdId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(createdId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request in createPet: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
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

    // --- DTOs ---

    @Schema(name = "PetRequest", description = "Request payload to create or update a Pet")
    public static class PetRequest {
        @Schema(description = "Public business pet id", example = "pet-123")
        private String petId;

        @Schema(description = "Pet name", example = "Whiskers")
        private String name;

        @Schema(description = "Species (cat/dog/etc)", example = "cat")
        private String species;

        @Schema(description = "Breed description", example = "Siamese")
        private String breed;

        @Schema(description = "Age in years", example = "2")
        private Integer age;

        @Schema(description = "Gender (M/F/unknown)", example = "F")
        private String gender;

        @Schema(description = "Imported source", example = "Petstore")
        private String importedFrom;

        @Schema(description = "Friendly description")
        private String description;

        @Schema(description = "Photo URLs")
        private List<String> photoUrls;

        @Schema(description = "Status (available/pending/adopted)", example = "AVAILABLE")
        private String status;

        @Schema(description = "Tags", example = "[\"playful\",\"lapcat\"]")
        private List<String> tags;

        public PetRequest() {}

        public String getPetId() {
            return petId;
        }

        public void setPetId(String petId) {
            this.petId = petId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSpecies() {
            return species;
        }

        public void setSpecies(String species) {
            this.species = species;
        }

        public String getBreed() {
            return breed;
        }

        public void setBreed(String breed) {
            this.breed = breed;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public String getImportedFrom() {
            return importedFrom;
        }

        public void setImportedFrom(String importedFrom) {
            this.importedFrom = importedFrom;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getPhotoUrls() {
            return photoUrls;
        }

        public void setPhotoUrls(List<String> photoUrls) {
            this.photoUrls = photoUrls;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    @Schema(name = "PetResponse", description = "Pet entity response payload")
    public static class PetResponse {
        @Schema(description = "Technical id (entity store id)")
        private String technicalId;

        @Schema(description = "Public business pet id", example = "pet-123")
        private String petId;

        @Schema(description = "Pet name", example = "Whiskers")
        private String name;

        @Schema(description = "Species (cat/dog/etc)", example = "cat")
        private String species;

        @Schema(description = "Breed description", example = "Siamese")
        private String breed;

        @Schema(description = "Age in years", example = "2")
        private Integer age;

        @Schema(description = "Gender (M/F/unknown)", example = "F")
        private String gender;

        @Schema(description = "Imported source", example = "Petstore")
        private String importedFrom;

        @Schema(description = "Friendly description")
        private String description;

        @Schema(description = "Photo URLs")
        private List<String> photoUrls;

        @Schema(description = "Status (available/pending/adopted)", example = "AVAILABLE")
        private String status;

        @Schema(description = "Tags", example = "[\"playful\",\"lapcat\"]")
        private List<String> tags;

        public PetResponse() {}

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }

        public String getPetId() {
            return petId;
        }

        public void setPetId(String petId) {
            this.petId = petId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSpecies() {
            return species;
        }

        public void setSpecies(String species) {
            this.species = species;
        }

        public String getBreed() {
            return breed;
        }

        public void setBreed(String breed) {
            this.breed = breed;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public String getImportedFrom() {
            return importedFrom;
        }

        public void setImportedFrom(String importedFrom) {
            this.importedFrom = importedFrom;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getPhotoUrls() {
            return photoUrls;
        }

        public void setPhotoUrls(List<String> photoUrls) {
            this.photoUrls = photoUrls;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    @Schema(name = "TechnicalIdResponse", description = "Response containing technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of created entity", example = "b3d7f3a2-...-9f3a")
        private String technicalId;

        public TechnicalIdResponse() {}

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}