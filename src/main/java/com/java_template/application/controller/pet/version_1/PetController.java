```java
package com.java_template.application.controller.pet.version_1;

import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@RestController
@RequestMapping("/pets")
@RequiredArgsConstructor
@Tag(name = "Pet Controller", description = "Operations for managing pets")
public class PetController {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping
    @Operation(summary = "Create a new pet", description = "Creates a new pet and returns its technical ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pet created successfully", content = @Content(schema = @Schema(implementation = PetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PetResponse> createPet(@RequestBody PetRequest petRequest) {
        try {
            Pet pet = new Pet();
            pet.setName(petRequest.getName());
            pet.setType(petRequest.getType());
            pet.setBreed(petRequest.getBreed());
            pet.setAge(petRequest.getAge());
            pet.setStatus(petRequest.getStatus());

            CompletableFuture<UUID> idFuture = entityService.addItem(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, pet);
            UUID entityId = idFuture.get();
            return ResponseEntity.ok(new PetResponse(entityId.toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    @Operation(summary = "Retrieve a pet by technical ID", description = "Returns a pet's details by its technical ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pet retrieved successfully", content = @Content(schema = @Schema(implementation = Pet.class))),
        @ApiResponse(responseCode = "404", description = "Pet not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Pet> getPetById(
            @Parameter(name = "technicalId", description = "Technical ID of the pet") @PathVariable String technicalId) {
        try {
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            JsonNode data = dataPayload != null ? dataPayload.getData() : null;
            Pet pet = objectMapper.treeToValue(data, Pet.class);
            return ResponseEntity.ok(pet);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    static class PetRequest {
        private String name;
        private String type;
        private String breed;
        private Integer age;
        private String status;
    }

    @Data
    static class PetResponse {
        private String technicalId;

        public PetResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
```