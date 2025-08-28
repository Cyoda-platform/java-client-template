package com.java_template.application.controller.owner.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.java_template.application.entity.owner.version_1.Owner;
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
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/owners")
@Tag(name = "Owner", description = "Owner entity API (version 1)")
public class OwnerController {

    private static final Logger logger = LoggerFactory.getLogger(OwnerController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OwnerController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Owner", description = "Create a new Owner entity. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateOwnerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<CreateOwnerResponse> createOwner(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner create payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateOwnerRequest.class)))
            @RequestBody CreateOwnerRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            // Map request DTO to entity
            Owner ownerEntity = objectMapper.convertValue(request, Owner.class);
            // Ensure createdAt basic field (controller may set basic metadata, not business logic)
            if (ownerEntity.getCreatedAt() == null) {
                ownerEntity.setCreatedAt(Instant.now());
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Owner.ENTITY_NAME,
                    Owner.ENTITY_VERSION,
                    ownerEntity
            );
            UUID technicalId = idFuture.get();
            CreateOwnerResponse response = new CreateOwnerResponse();
            response.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createOwner: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("ExecutionException while creating Owner", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating Owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Owner", description = "Retrieve an Owner entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = OwnerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<OwnerResponse> getOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID uuid = UUID.fromString(technicalId);

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(uuid);
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            OwnerResponse response = objectMapper.treeToValue((JsonNode) node, OwnerResponse.class);
            response.setTechnicalId(technicalId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getOwner: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("ExecutionException while fetching Owner", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while fetching Owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Request/Response DTOs as static classes

    @Data
    @Schema(name = "CreateOwnerRequest", description = "Payload to create an Owner")
    public static class CreateOwnerRequest {
        @Schema(description = "Business id", example = "OWN-10")
        private String id;
        @Schema(description = "Full name", example = "Alex Doe")
        private String fullName;
        @Schema(description = "Email address", example = "alex@example.com")
        private String email;
        @Schema(description = "Phone number", example = "+123456789")
        private String phone;
        @Schema(description = "Address", example = "123 Cat St")
        private String address;
        @Schema(description = "Short bio", example = "Loves cats")
        private String bio;
        @Schema(description = "Favorite pet IDs", example = "[]")
        private java.util.List<String> favoritePetIds;
        @Schema(description = "Role", example = "owner")
        private String role;
    }

    @Data
    @Schema(name = "CreateOwnerResponse", description = "Response after creating an Owner")
    public static class CreateOwnerResponse {
        @Schema(description = "Technical id of the created entity", example = "tech-owner-0001")
        private String technicalId;
    }

    @Data
    @Schema(name = "OwnerResponse", description = "Owner entity response")
    public static class OwnerResponse {
        @Schema(description = "Technical id", example = "tech-owner-0001")
        private String technicalId;

        @Schema(description = "Business id", example = "OWN-10")
        private String id;

        @Schema(description = "Full name", example = "Alex Doe")
        private String fullName;

        @Schema(description = "Email address", example = "alex@example.com")
        private String email;

        @Schema(description = "Phone number", example = "+123456789")
        private String phone;

        @Schema(description = "Address", example = "123 Cat St")
        private String address;

        @Schema(description = "Short bio", example = "Loves cats")
        private String bio;

        @Schema(description = "Favorite pet IDs")
        private java.util.List<String> favoritePetIds;

        @Schema(description = "Role", example = "owner")
        private String role;

        @Schema(description = "Created at (ISO timestamp)", example = "2025-08-28T12:00:00Z")
        private Instant createdAt;

        @Schema(description = "Updated at (ISO timestamp)", example = "2025-08-28T12:00:00Z")
        private Instant updatedAt;
    }
}