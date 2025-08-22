package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/pets")
@Tag(name = "Pet", description = "Pet entity operations")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PetController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Pet", description = "Create a new Pet entity and start its workflow. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
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

            ObjectNode data = objectMapper.valueToTree(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    data
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createPet request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(errorBody(iae.getMessage(), null));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleUnwrappedException(cause, "createPet");
        } catch (Exception e) {
            logger.error("Unexpected error in createPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(e.getMessage(), null));
        }
    }

    @Operation(summary = "Get Pet", description = "Retrieve a Pet entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    tid
            );
            ObjectNode item = itemFuture.get();
            PetResponse resp = new PetResponse(technicalId, item);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId in getPet: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(errorBody(iae.getMessage(), null));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleUnwrappedException(cause, "getPet");
        } catch (Exception e) {
            logger.error("Unexpected error in getPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(e.getMessage(), null));
        }
    }

    @Operation(summary = "List Pets", description = "List all Pet entities (read-only).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = Pet.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping
    public ResponseEntity<?> listPets() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleUnwrappedException(cause, "listPets");
        } catch (Exception e) {
            logger.error("Unexpected error in listPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(e.getMessage(), null));
        }
    }

    @Operation(summary = "Search Pets", description = "Search Pet entities by simple field filters (supports basic operators).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = Pet.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping("/search")
    public ResponseEntity<?> searchPets(
            @RequestParam(required = false) String species,
            @RequestParam(required = false) String location) {
        try {
            SearchConditionRequest condition = null;
            if (species != null && location != null) {
                condition = SearchConditionRequest.group("AND",
                        Condition.of("$.species", "EQUALS", species),
                        Condition.of("$.location", "EQUALS", location)
                );
            } else if (species != null) {
                condition = SearchConditionRequest.group("AND",
                        Condition.of("$.species", "EQUALS", species)
                );
            } else if (location != null) {
                condition = SearchConditionRequest.group("AND",
                        Condition.of("$.location", "EQUALS", location)
                );
            } else {
                // No filters provided — return all
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION)
                );
                ArrayNode items = itemsFuture.get();
                return ResponseEntity.ok(items);
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search params in searchPets: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(errorBody(iae.getMessage(), null));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleUnwrappedException(cause, "searchPets");
        } catch (Exception e) {
            logger.error("Unexpected error in searchPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(e.getMessage(), null));
        }
    }

    @Operation(summary = "Update Pet", description = "Update an existing Pet by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet payload", required = true,
                    content = @Content(schema = @Schema(implementation = PetRequest.class)))
            @RequestBody PetRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            UUID tid = UUID.fromString(technicalId);
            ObjectNode data = objectMapper.valueToTree(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    tid,
                    data
            );
            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid updatePet request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(errorBody(iae.getMessage(), null));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleUnwrappedException(cause, "updatePet");
        } catch (Exception e) {
            logger.error("Unexpected error in updatePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(e.getMessage(), null));
        }
    }

    @Operation(summary = "Delete Pet", description = "Delete a Pet by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    tid
            );
            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId in deletePet: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(errorBody(iae.getMessage(), null));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleUnwrappedException(cause, "deletePet");
        } catch (Exception e) {
            logger.error("Unexpected error in deletePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(e.getMessage(), null));
        }
    }

    private ResponseEntity<?> handleUnwrappedException(Throwable cause, String op) {
        if (cause instanceof NoSuchElementException) {
            logger.warn("{} not found: {}", op, cause.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(cause.getMessage(), null));
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("{} bad request: {}", op, cause.getMessage());
            return ResponseEntity.badRequest().body(errorBody(cause.getMessage(), null));
        } else {
            logger.error("Unhandled error in {}: {}", op, cause.getMessage(), cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(cause.getMessage(), null));
        }
    }

    private ObjectNode errorBody(String message, Object details) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("error", message == null ? "unexpected error" : message);
        if (details != null) {
            node.set("details", objectMapper.valueToTree(details));
        }
        return node;
    }

    // DTOs

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "PetRequest", description = "Pet create/update request payload")
    public static class PetRequest {
        @Schema(description = "Business id (human-readable)")
        private String id;

        @Schema(description = "Pet name")
        private String name;

        @Schema(description = "Species (dog/cat/etc)")
        private String species;

        @Schema(description = "Breed description")
        private String breed;

        @Schema(description = "Age in months")
        private Integer ageMonths;

        @Schema(description = "Sex (M/F/Unknown)")
        private String sex;

        @Schema(description = "Status")
        private String status;

        @Schema(description = "Images URLs")
        private java.util.List<String> images;

        @Schema(description = "Thumbnail URLs")
        private java.util.List<String> imageThumbs;

        @Schema(description = "Location (store or foster)")
        private String location;

        @Schema(description = "Vaccination summary")
        private String vaccinationSummary;

        @Schema(description = "Medical notes")
        private String medicalNotes;

        @Schema(description = "Tags map")
        private java.util.Map<String, String> tags;

        @Schema(description = "Added at ISO datetime")
        private String addedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID assigned by the system")
        private String technicalId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "PetResponse", description = "Response wrapping technicalId and the pet entity")
    public static class PetResponse {
        @Schema(description = "Technical ID of the entity")
        private String technicalId;

        @Schema(description = "Pet entity object")
        private JsonNode entity;
    }
}