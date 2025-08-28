package com.java_template.application.controller.owner.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.owner.version_1.Owner;
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
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/owners")
@Tag(name = "Owner Controller", description = "Controller proxy for Owner entity (version 1)")
public class OwnerController {

    private static final Logger logger = LoggerFactory.getLogger(OwnerController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OwnerController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Owner", description = "Create a new Owner entity. Returns only the technicalId (UUID) of the created entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createOwner(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner create request", required = true,
                    content = @Content(schema = @Schema(implementation = OwnerCreateRequest.class)))
            @RequestBody OwnerCreateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getName() == null || request.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                throw new IllegalArgumentException("email is required");
            }

            Owner ownerEntity = new Owner();
            // Set a business id to satisfy entity validation expected by workflows/services
            ownerEntity.setId(UUID.randomUUID().toString());
            ownerEntity.setName(request.getName());
            ownerEntity.setEmail(request.getEmail());
            ownerEntity.setPhone(request.getPhone());
            ownerEntity.setVerified(Boolean.FALSE);
            ownerEntity.setPetsOwned(new ArrayList<>());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Owner.ENTITY_NAME,
                    Owner.ENTITY_VERSION,
                    ownerEntity
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(technicalId.toString());
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create owner: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException creating owner", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while creating owner", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Owner", description = "Retrieve an Owner entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OwnerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Owner not found");
            }

            ObjectNode node = (ObjectNode) dataPayload.getData();
            OwnerResponse response = objectMapper.treeToValue(node, OwnerResponse.class);
            response.setTechnicalId(technicalId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get owner: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException retrieving owner", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while retrieving owner", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "OwnerCreateRequest", description = "Request payload to create an Owner")
    public static class OwnerCreateRequest {
        @Schema(description = "Owner full name", example = "Ava Catlover", required = true)
        private String name;

        @Schema(description = "Contact email", example = "ava@example.com", required = true)
        private String email;

        @Schema(description = "Contact phone", example = "+123456789", required = false)
        private String phone;
    }

    @Data
    @Schema(name = "OwnerResponse", description = "Response payload for Owner entity")
    public static class OwnerResponse {
        @Schema(description = "Technical ID (UUID) of the stored entity", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        private String technicalId;

        @Schema(description = "Business identifier of the owner", example = "owner-42")
        private String id;

        @Schema(description = "Owner full name", example = "Ava Catlover")
        private String name;

        @Schema(description = "Contact email", example = "ava@example.com")
        private String email;

        @Schema(description = "Contact phone", example = "+123456789")
        private String phone;

        @Schema(description = "Verification status")
        private Boolean verified;

        @Schema(description = "List of pet business ids owned by this owner")
        private List<String> petsOwned;
    }
}