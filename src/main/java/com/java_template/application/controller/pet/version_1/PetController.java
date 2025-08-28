```java
package com.java_template.application.controller.pet.version_1;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/pets")
@Tag(name = "Pet", description = "Operations related to Pets")
public class PetController {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    public PetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Operation(summary = "Add a new pet", description = "Add a new pet to the system")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pet added successfully", content = @Content(schema = @Schema(implementation = PetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PetResponse> addPet(@RequestBody PetRequest petRequest) {
        try {
            Pet pet = new Pet();
            pet.setName(petRequest.getName());
            pet.setType(petRequest.getType());
            pet.setAge(petRequest.getAge());
            pet.setStatus("AVAILABLE");

            CompletableFuture<UUID> idFuture = entityService.addItem(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, pet);
            UUID entityId = idFuture.get();

            return ResponseEntity.ok(new PetResponse(entityId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input", e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Error during processing", e);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/{technicalId}")
    @Operation(summary = "Retrieve a pet by ID", description = "Get pet details for a specific ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pet found", content = @Content(schema = @Schema(implementation = Pet.class))),
            @ApiResponse(responseCode = "404", description = "Pet not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Pet> getPet(@Parameter(name = "technicalId", description = "Technical ID of the pet") @PathVariable String technicalId) {
        try {
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            Pet pet = objectMapper.treeToValue(dataPayload.getData(), Pet.class);
            return ResponseEntity.ok(pet);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technical ID", e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Error during processing", e);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(500).build();
        }
    }

    // Static DTO classes for request/response
    static class PetRequest {
        @Schema(description = "Name of the pet")
        private String name;
        @Schema(description = "Type of the pet")
        private String type;
        @Schema(description = "Age of the pet")
        private Integer age;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
    }

    static class PetResponse {
        @Schema(description = "Technical ID of the pet")
        private String technicalId;

        public PetResponse(String technicalId) {
            this.technicalId = technicalId;
        }

        public String getTechnicalId() { return technicalId; }
    }
}
```