package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/pets")
@Tag(name = "Pet API", description = "Endpoints for managing pets")
public class PetController {
    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PetController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Pet", description = "Create a new pet and trigger associated workflows. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPet(@RequestBody CreatePetRequest request) {
        try {
            // basic validation
            if (request.getName() == null || request.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getSpecies() == null || request.getSpecies().isBlank()) {
                throw new IllegalArgumentException("species is required");
            }

            Pet pet = new Pet();
            pet.setId(request.getId());
            pet.setName(request.getName());
            pet.setSpecies(request.getSpecies());
            pet.setBreed(request.getBreed());
            pet.setAge(request.getAge());
            pet.setGender(request.getGender());
            pet.setDescription(request.getDescription());
            pet.setImages(request.getImages());
            pet.setHealthSummary(request.getHealthSummary());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    pet
            );
            UUID technicalId = idFuture.get();

            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to create pet: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", cause.getMessage()));
            } else {
                logger.error("Execution exception while creating pet", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while creating pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @Operation(summary = "Get Pet", description = "Retrieve a pet by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPet(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            PetResponse resp = objectMapper.treeToValue(node, PetResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to get pet: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", cause.getMessage()));
            } else {
                logger.error("Execution exception while retrieving pet", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        }
    }

    // DTOs
    @Data
    static class CreatePetRequest {
        @Schema(description = "Business id (optional)")
        private String id;
        @Schema(description = "Pet name", required = true)
        private String name;
        @Schema(description = "Species", required = true)
        private String species;
        @Schema(description = "Breed")
        private String breed;
        @Schema(description = "Age in years")
        private Integer age;
        @Schema(description = "Gender")
        private String gender;
        @Schema(description = "Description")
        private String description;
        @Schema(description = "Image URLs")
        private List<String> images;
        @Schema(description = "Health summary")
        private String healthSummary;
    }

    @Data
    static class CreateResponse {
        @Schema(description = "Technical id assigned to the entity")
        private String technicalId;
    }

    @Data
    static class PetResponse {
        @Schema(description = "Technical id")
        private String technicalId;
        @Schema(description = "Business id (optional)")
        private String id;
        @Schema(description = "Pet name")
        private String name;
        @Schema(description = "Species")
        private String species;
        @Schema(description = "Breed")
        private String breed;
        @Schema(description = "Age in years")
        private Integer age;
        @Schema(description = "Gender")
        private String gender;
        @Schema(description = "Lifecycle state")
        private String lifecycleState;
        @Schema(description = "Status (backwards compatible)")
        private String status;
        @Schema(description = "Description")
        private String description;
        @Schema(description = "Image URLs")
        private List<String> images;
        @Schema(description = "Health summary")
        private String healthSummary;
        @Schema(description = "Created at")
        private String createdAt;
        @Schema(description = "Updated at")
        private String updatedAt;
    }

    @Data
    static class ErrorResponse {
        private final String code;
        private final String message;
    }
}
