package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.validation.Valid;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dull controller proxying Pet requests to EntityService.
 * All business logic lives in workflows/processors; controller only proxies.
 */
@RestController
@RequestMapping("/api/v1/pets")
@Tag(name = "Pet API", description = "CRUD and listing endpoints for Pet entity (proxy to EntityService)")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Pet", description = "Create a single Pet. Returns only technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPet(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet creation payload")
        @Valid @RequestBody PetRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            // Convert request DTO to ObjectNode for the entity service
            ObjectNode data = objectMapper.valueToTree(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                data
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to create pet: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException while creating pet", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while creating pet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple Pets", description = "Bulk create Pets. Returns list of technicalIds.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createPetsBatch(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of pet creation payloads")
        @Valid @RequestBody List<PetRequest> requests
    ) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request list is required and must not be empty");
            }

            ArrayNode data = objectMapper.valueToTree(requests);
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                data
            );

            List<UUID> ids = idsFuture.get();
            List<TechnicalIdResponse> resp = ids.stream().map(id -> new TechnicalIdResponse(id.toString())).toList();
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid batch create request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException while creating pets batch", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating pets batch", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while creating pets batch", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Pet by technicalId", description = "Retrieve a pet by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPetById(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                id
            );

            ObjectNode node = itemFuture.get();
            PetResponse resp = objectMapper.treeToValue(node, PetResponse.class);
            // ensure technicalId field is present in response
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid getPetById request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException while retrieving pet", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving pet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "List Pets", description = "Retrieve all pets or filter by a simple field condition. Use filterField/operator/value for simple filtering.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listPets(
        @RequestParam(required = false) String filterField,
        @RequestParam(required = false, defaultValue = "EQUALS") String operator,
        @RequestParam(required = false) String value
    ) {
        try {
            if ((filterField == null && value != null) || (filterField != null && value == null)) {
                throw new IllegalArgumentException("Both filterField and value must be provided together");
            }

            ArrayNode arrayNode;
            if (filterField != null && value != null) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.%s".formatted(filterField), operator, value)
                );

                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    condition,
                    true
                );
                arrayNode = filteredItemsFuture.get();
            } else {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION)
                );
                arrayNode = itemsFuture.get();
            }

            List<PetResponse> pets = objectMapper.readerForListOf(PetResponse.class).readValue(arrayNode);
            return ResponseEntity.ok(pets);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid listPets request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException while listing pets", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing pets", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while listing pets", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Pet", description = "Update a Pet by technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePet(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet update payload")
        @Valid @RequestBody PetRequest request
    ) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            UUID id = UUID.fromString(technicalId);
            ObjectNode data = objectMapper.valueToTree(request);

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                id,
                data
            );

            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid updatePet request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException while updating pet", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while updating pet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Pet", description = "Delete a Pet by technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePet(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId is required");
            }

            UUID id = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                id
            );

            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deletedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid deletePet request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException while deleting pet", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting pet", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(description = "Pet creation / update request payload")
    public static class PetRequest {
        @Schema(description = "Domain id from source if present")
        private String id;

        @Schema(description = "Pet name")
        private String name;

        @Schema(description = "Species (cat/dog/etc)")
        private String species;

        @Schema(description = "Breed description")
        private String breed;

        @Schema(description = "Age in integer (years or months)")
        private Integer age;

        @Schema(description = "Gender (male/female/unknown)")
        private String gender;

        @Schema(description = "Color (visual color)")
        private String color;

        @Schema(description = "Free text description")
        private String description;

        @Schema(description = "Photos (array of URLs)")
        private List<String> photos;

        @Schema(description = "Status (available/pending/adopted/removed)")
        private String status;

        @Schema(description = "Location (shelter or city)")
        private String location;

        @Schema(description = "Tags for filtering")
        private List<String> tags;

        @Schema(description = "Source metadata (raw source info)")
        private Object source_metadata;
    }

    @Data
    @Schema(description = "Pet response payload")
    public static class PetResponse {
        @Schema(description = "Technical id")
        private String technicalId;

        @Schema(description = "Domain id from source if present")
        private String id;

        @Schema(description = "Pet name")
        private String name;

        @Schema(description = "Species (cat/dog/etc)")
        private String species;

        @Schema(description = "Breed description")
        private String breed;

        @Schema(description = "Status (available/pending/adopted/removed)")
        private String status;

        @Schema(description = "Photos (array of URLs)")
        private List<String> photos;

        @Schema(description = "Age in integer (years or months)")
        private Integer age;

        @Schema(description = "Gender")
        private String gender;

        @Schema(description = "Color")
        private String color;

        @Schema(description = "Description")
        private String description;

        @Schema(description = "Location")
        private String location;

        @Schema(description = "Tags")
        private List<String> tags;

        @Schema(description = "Source metadata")
        private Object source_metadata;
    }

    @Data
    @Schema(description = "TechnicalId response (returned by POST/PUT/DELETE)")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id (UUID)")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}