```java
package com.java_template.application.controller.adoption.version_1;

import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/adoptions")
public class AdoptionController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AdoptionController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create a new adoption", description = "Creates a new adoption and returns the technical ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Adoption created successfully", content = @Content(schema = @Schema(implementation = CreateAdoptionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<CreateAdoptionResponse> createAdoption(@RequestBody CreateAdoptionRequest request) {
        try {
            Adoption adoption = new Adoption();
            adoption.setPetId(request.getPetId());
            adoption.setUserId(request.getUserId());
            adoption.setStatus("PENDING"); // Initial status

            CompletableFuture<UUID> idFuture = entityService.addItem(Adoption.ENTITY_NAME, Adoption.ENTITY_VERSION, adoption);
            UUID entityId = idFuture.get();
            return ResponseEntity.ok(new CreateAdoptionResponse(entityId.toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Retrieve an adoption by technical ID", description = "Returns the adoption details for the provided technical ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Adoption retrieved successfully", content = @Content(schema = @Schema(implementation = Adoption.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<Adoption> getAdoption(@PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(id);
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload != null) {
                return ResponseEntity.ok(objectMapper.treeToValue(dataPayload.getData(), Adoption.class));
            }
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Static classes for request/response DTOs
    static class CreateAdoptionRequest {
        @Schema(description = "ID of the pet being adopted")
        private String petId;

        @Schema(description = "ID of the user adopting the pet")
        private String userId;

        public String getPetId() {
            return petId;
        }

        public void setPetId(String petId) {
            this.petId = petId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }
    }

    static class CreateAdoptionResponse {
        @Schema(description = "Technical ID of the created adoption")
        private String technicalId;

        public CreateAdoptionResponse(String technicalId) {
            this.technicalId = technicalId;
        }

        public String getTechnicalId() {
            return technicalId;
        }
    }
}
```