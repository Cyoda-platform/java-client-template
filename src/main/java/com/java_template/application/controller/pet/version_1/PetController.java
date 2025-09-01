package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
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
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;
import java.util.*;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/pets")
@Tag(name = "Pet", description = "Operations for Pet entity (version 1) - controller proxies to EntityService")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PetController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create a pet", description = "Add a single Pet entity. Returns technicalId (UUID) of the persisted entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<String> addPet(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet payload", required = true,
                    content = @Content(schema = @Schema(implementation = PetRequest.class)))
            @RequestBody PetRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            Pet pet = new Pet();
            // Copy fields - no business logic here
            pet.setId(request.getId());
            pet.setName(request.getName());
            pet.setSpecies(request.getSpecies());
            pet.setBreed(request.getBreed());
            pet.setAge(request.getAge());
            pet.setStatus(request.getStatus());
            pet.setSourceUrl(request.getSourceUrl());
            pet.setPhotoUrls(request.getPhotoUrls());
            pet.setVaccinations(request.getVaccinations());

            UUID technicalId = entityService.addItem(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, pet).get();
            return ResponseEntity.ok(technicalId.toString());
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to addPet: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addPet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in addPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple pets", description = "Add multiple Pet entities. Returns list of technicalIds (UUIDs).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/batch", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> addPetsBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Pet payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetRequest.class))))
            @RequestBody List<PetRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request list must not be empty");
            }
            List<Pet> entities = new ArrayList<>();
            for (PetRequest req : requests) {
                Pet pet = new Pet();
                pet.setId(req.getId());
                pet.setName(req.getName());
                pet.setSpecies(req.getSpecies());
                pet.setBreed(req.getBreed());
                pet.setAge(req.getAge());
                pet.setStatus(req.getStatus());
                pet.setSourceUrl(req.getSourceUrl());
                pet.setPhotoUrls(req.getPhotoUrls());
                pet.setVaccinations(req.getVaccinations());
                entities.add(pet);
            }
            List<UUID> ids = entityService.addItems(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, entities).get();
            List<String> idStrings = new ArrayList<>();
            for (UUID id : ids) idStrings.add(id.toString());
            return ResponseEntity.ok(idStrings);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to addPetsBatch: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addPetsBatch", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in addPetsBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get a pet by technicalId", description = "Retrieve a single Pet entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> getPetById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            DataPayload dataPayload = entityService.getItem(UUID.fromString(technicalId)).get();
            if (dataPayload == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Entity not found");
            }
            JsonNode data = dataPayload.getData();
            PetResponse response = objectMapper.treeToValue(data, PetResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to getPetById: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getPetById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getPetById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List all pets", description = "Retrieve all Pet entities (paged via entity service defaults).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = "application/json")
    public ResponseEntity<?> listPets() {
        try {
            List<DataPayload> dataPayloads = entityService.getItems(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, null, null, null).get();
            List<PetResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    PetResponse resp = objectMapper.treeToValue(data, PetResponse.class);
                    // Optionally populate technicalId from meta if present
                    if (payload.getMeta() != null && payload.getMeta().has("entityId")) {
                        resp.setTechnicalId(payload.getMeta().get("entityId").asText());
                    }
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in listPets", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in listPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search pets by species", description = "Search pets by simple field condition (species).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/search", produces = "application/json")
    public ResponseEntity<?> searchPetsBySpecies(
            @Parameter(name = "species", description = "Species to filter by")
            @RequestParam(name = "species", required = true) String species) {
        try {
            if (species == null || species.isBlank()) {
                throw new IllegalArgumentException("species query parameter is required");
            }
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.species", "IEQUALS", species));
            List<DataPayload> dataPayloads = entityService.getItemsByCondition(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, condition, true).get();
            List<PetResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    PetResponse resp = objectMapper.treeToValue(data, PetResponse.class);
                    if (payload.getMeta() != null && payload.getMeta().has("entityId")) {
                        resp.setTechnicalId(payload.getMeta().get("entityId").asText());
                    }
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to searchPetsBySpecies: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchPetsBySpecies", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in searchPetsBySpecies", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs for requests/responses
    @Data
    @Schema(name = "PetRequest", description = "Request payload to create or update a Pet")
    public static class PetRequest {
        @Schema(description = "Business id (serialized UUID). If absent the caller may provide one.", example = "pet-12")
        private String id;
        @Schema(description = "Pet name", example = "Whiskers")
        private String name;
        @Schema(description = "Species (e.g., cat, dog)", example = "cat")
        private String species;
        @Schema(description = "Breed information", example = "Siamese")
        private String breed;
        @Schema(description = "Age in years", example = "3")
        private Integer age;
        @Schema(description = "Status (AVAILABLE, ADOPTED, etc.)", example = "AVAILABLE")
        private String status;
        @Schema(description = "Source URL of original record", example = "https://petstore.example/api/pets/12")
        private String sourceUrl;
        @Schema(description = "Photo URLs", example = "[\"https://.../1.png\"]")
        private List<String> photoUrls;
        @Schema(description = "Vaccination records", example = "[\"rabies\",\"distemper\"]")
        private List<String> vaccinations;
    }

    @Data
    @Schema(name = "PetResponse", description = "Response payload for Pet entity")
    public static class PetResponse {
        @Schema(description = "Business id (serialized UUID)", example = "pet-12")
        private String id;
        @Schema(description = "Technical entity id assigned by the platform", example = "5f47ac1e-...")
        private String technicalId;
        @Schema(description = "Pet name", example = "Whiskers")
        private String name;
        @Schema(description = "Species (e.g., cat, dog)", example = "cat")
        private String species;
        @Schema(description = "Breed information", example = "Siamese")
        private String breed;
        @Schema(description = "Age in years", example = "3")
        private Integer age;
        @Schema(description = "Status (AVAILABLE, ADOPTED, etc.)", example = "AVAILABLE")
        private String status;
        @Schema(description = "Source URL of original record", example = "https://petstore.example/api/pets/12")
        private String sourceUrl;
        @Schema(description = "Photo URLs", example = "[\"https://.../1.png\"]")
        private List<String> photoUrls;
        @Schema(description = "Vaccination records", example = "[\"rabies\",\"distemper\"]")
        private List<String> vaccinations;
    }
}