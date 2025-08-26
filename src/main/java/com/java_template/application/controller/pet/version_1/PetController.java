package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/pets")
@Tag(name = "Pet", description = "Operations for Pet entity")
public class PetController {
    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PetController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Pet by technicalId", description = "Retrieve a Pet entity by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPetById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            ObjectNode node = entityService.getItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();

            PetResponse response = objectMapper.treeToValue(node, PetResponse.class);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in getPetById: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.info("Pet not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Illegal argument from service in getPetById: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getPetById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getPetById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Pets", description = "Retrieve all Pet entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listPets() {
        try {
            ArrayNode arr = entityService.getItems(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION)
            ).get();

            List<PetResponse> list = objectMapper.convertValue(arr, new TypeReference<List<PetResponse>>() {});
            return ResponseEntity.ok(list);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in listPets: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Illegal argument from service in listPets: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in listPets", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing pets", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in listPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create Pet", description = "Add a single Pet entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = AddPetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> addPet(
            @RequestBody(description = "Pet to add")
            @org.springframework.web.bind.annotation.RequestBody AddPetRequest request) {
        try {
            Pet data = objectMapper.convertValue(request, Pet.class);
            UUID created = entityService.addItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    data
            ).get();

            AddPetResponse resp = new AddPetResponse();
            resp.setId(created.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in addPet: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Illegal argument from service in addPet: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addPet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in addPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create Pets (bulk)", description = "Add multiple Pet entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = BulkAddResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> addPetsBulk(
            @RequestBody(description = "Pets to add in bulk")
            @org.springframework.web.bind.annotation.RequestBody BulkAddRequest request) {
        try {
            List<Pet> data = objectMapper.convertValue(request.getPets(), new TypeReference<List<Pet>>() {});
            List<UUID> created = entityService.addItems(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    data
            ).get();

            BulkAddResponse resp = new BulkAddResponse();
            List<String> ids = new ArrayList<>();
            for (UUID u : created) ids.add(u.toString());
            resp.setIds(ids);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in addPetsBulk: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Illegal argument from service in addPetsBulk: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addPetsBulk", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding pets bulk", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in addPetsBulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Pet", description = "Update a Pet entity by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated", content = @Content(schema = @Schema(implementation = UpdatePetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId,
            @RequestBody(description = "Pet fields to update")
            @org.springframework.web.bind.annotation.RequestBody UpdatePetRequest request) {
        try {
            Pet data = objectMapper.convertValue(request, Pet.class);
            UUID updated = entityService.updateItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    data
            ).get();

            UpdatePetResponse resp = new UpdatePetResponse();
            resp.setId(updated.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in updatePet: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.info("Pet not found for update: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Illegal argument from service in updatePet: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updatePet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in updatePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Pet", description = "Delete a Pet entity by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(implementation = DeletePetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            UUID deleted = entityService.deleteItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();

            DeletePetResponse resp = new DeletePetResponse();
            resp.setId(deleted.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in deletePet: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.info("Pet not found for delete: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Illegal argument from service in deletePet: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deletePet", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in deletePet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Pets", description = "Search Pets by simple filters (species, status, name). All parameters are optional and combined with AND.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/search")
    public ResponseEntity<?> searchPets(
            @Parameter(name = "species", description = "Filter by species (EQUALS)")
            @RequestParam(required = false) String species,
            @Parameter(name = "status", description = "Filter by status (EQUALS)")
            @RequestParam(required = false) String status,
            @Parameter(name = "name", description = "Filter by name (IEQUALS)")
            @RequestParam(required = false) String name
    ) {
        try {
            List<Condition> conds = new ArrayList<>();
            if (species != null && !species.isBlank()) {
                conds.add(Condition.of("$.species", "EQUALS", species));
            }
            if (status != null && !status.isBlank()) {
                conds.add(Condition.of("$.status", "EQUALS", status));
            }
            if (name != null && !name.isBlank()) {
                conds.add(Condition.of("$.name", "IEQUALS", name));
            }

            SearchConditionRequest condition;
            if (conds.isEmpty()) {
                // No filters - return all items
                ArrayNode arr = entityService.getItems(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION)
                ).get();
                List<PetResponse> list = objectMapper.convertValue(arr, new TypeReference<List<PetResponse>>() {});
                return ResponseEntity.ok(list);
            } else {
                condition = SearchConditionRequest.group("AND", conds.toArray(new Condition[0]));
                ArrayNode arr = entityService.getItemsByCondition(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        condition,
                        true
                ).get();

                List<PetResponse> list = objectMapper.convertValue(arr, new TypeReference<List<PetResponse>>() {});
                return ResponseEntity.ok(list);
            }

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in searchPets: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Illegal argument from service in searchPets: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchPets", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching pets", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in searchPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs for request/response payloads

    @Data
    @Schema(name = "PetResponse", description = "Pet response payload")
    public static class PetResponse {
        @Schema(description = "Technical id", example = "pet_123")
        private String id;

        @Schema(description = "Source id from external system", example = "ps_987")
        private String sourceId;

        @Schema(description = "Pet name", example = "Mittens")
        private String name;

        @Schema(description = "Species", example = "cat")
        private String species;

        @Schema(description = "Breed description", example = "Tabby")
        private String breed;

        @Schema(description = "Age in years or months", example = "2")
        private Integer age;

        @Schema(description = "Status (available, adopted, foster)", example = "available")
        private String status;

        @Schema(description = "Tags", example = "[\"kitten\",\"friendly\"]")
        private List<String> tags;

        @Schema(description = "Image URLs", example = "[\"https://...\"]")
        private List<String> images;

        @Schema(description = "Description", example = "Friendly tabby kitten")
        private String description;

        @Schema(description = "Timestamp from source", example = "2025-08-25T12:00:00Z")
        private String sourceUpdatedAt;

        @Schema(description = "Record created timestamp", example = "2025-08-25T12:01:00Z")
        private String createdAt;

        @Schema(description = "Record updated timestamp", example = "2025-08-25T12:01:00Z")
        private String updatedAt;
    }

    @Data
    @Schema(name = "AddPetRequest", description = "Payload to add a Pet")
    public static class AddPetRequest {
        @Schema(description = "Technical id", example = "pet_123")
        private String id;

        @Schema(description = "Source id from external system", example = "ps_987")
        private String sourceId;

        @Schema(description = "Pet name", example = "Mittens")
        private String name;

        @Schema(description = "Species", example = "cat")
        private String species;

        @Schema(description = "Breed", example = "Tabby")
        private String breed;

        @Schema(description = "Age", example = "2")
        private Integer age;

        @Schema(description = "Status", example = "available")
        private String status;

        @Schema(description = "Tags")
        private List<String> tags;

        @Schema(description = "Images")
        private List<String> images;

        @Schema(description = "Description")
        private String description;

        @Schema(description = "Source updated at timestamp")
        private String sourceUpdatedAt;

        @Schema(description = "Created at timestamp")
        private String createdAt;

        @Schema(description = "Updated at timestamp")
        private String updatedAt;
    }

    @Data
    @Schema(name = "AddPetResponse", description = "Response when a Pet is created")
    public static class AddPetResponse {
        @Schema(description = "Created technical id", example = "uuid-string")
        private String id;
    }

    @Data
    @Schema(name = "BulkAddRequest", description = "Request to add multiple Pets")
    public static class BulkAddRequest {
        @Schema(description = "List of pets to add")
        private List<AddPetRequest> pets;
    }

    @Data
    @Schema(name = "BulkAddResponse", description = "Response for bulk add")
    public static class BulkAddResponse {
        @Schema(description = "List of created ids")
        private List<String> ids;
    }

    @Data
    @Schema(name = "UpdatePetRequest", description = "Payload to update a Pet")
    public static class UpdatePetRequest {
        @Schema(description = "Source id from external system", example = "ps_987")
        private String sourceId;

        @Schema(description = "Pet name", example = "Mittens")
        private String name;

        @Schema(description = "Species", example = "cat")
        private String species;

        @Schema(description = "Breed", example = "Tabby")
        private String breed;

        @Schema(description = "Age", example = "2")
        private Integer age;

        @Schema(description = "Status", example = "available")
        private String status;

        @Schema(description = "Tags")
        private List<String> tags;

        @Schema(description = "Images")
        private List<String> images;

        @Schema(description = "Description")
        private String description;

        @Schema(description = "Source updated at timestamp")
        private String sourceUpdatedAt;

        @Schema(description = "Updated at timestamp")
        private String updatedAt;
    }

    @Data
    @Schema(name = "UpdatePetResponse", description = "Response when a Pet is updated")
    public static class UpdatePetResponse {
        @Schema(description = "Updated technical id", example = "uuid-string")
        private String id;
    }

    @Data
    @Schema(name = "DeletePetResponse", description = "Response when a Pet is deleted")
    public static class DeletePetResponse {
        @Schema(description = "Deleted technical id", example = "uuid-string")
        private String id;
    }
}