package com.java_template.application.controller.owner.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
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

@Tag(name = "Owner API", description = "API for Owner entity (version 1)")
@RestController
@RequestMapping("/api/v1/owners")
public class OwnerController {

    private static final Logger logger = LoggerFactory.getLogger(OwnerController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OwnerController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Owner", description = "Persist an Owner entity and trigger the Owner workflow. Returns only the technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<TechnicalIdResponse> createOwner(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner create request", required = true,
                    content = @Content(schema = @Schema(implementation = OwnerCreateRequest.class)))
            @RequestBody OwnerCreateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            // Basic format validation (no business logic)
            if (request.getOwnerId() == null || request.getOwnerId().isBlank()) {
                throw new IllegalArgumentException("ownerId is required");
            }
            if (request.getName() == null || request.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getContactInfo() == null || request.getContactInfo().getEmail() == null || request.getContactInfo().getEmail().isBlank()) {
                throw new IllegalArgumentException("contactInfo.email is required");
            }

            Owner owner = new Owner();
            owner.setOwnerId(request.getOwnerId());
            owner.setName(request.getName());
            owner.setAddress(request.getAddress());
            owner.setRole(request.getRole());
            // Optional fields
            if (request.getSavedPets() != null) owner.setSavedPets(request.getSavedPets());
            if (request.getAdoptedPets() != null) owner.setAdoptedPets(request.getAdoptedPets());
            if (request.getVerificationStatus() != null) owner.setVerificationStatus(request.getVerificationStatus());
            // Map contactInfo
            owner.setContactEmail(request.getContactInfo().getEmail());
            owner.setContactPhone(request.getContactInfo().getPhone());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Owner.ENTITY_NAME,
                    Owner.ENTITY_VERSION,
                    owner
            );
            UUID entityId = idFuture.get();

            TechnicalIdResponse response = new TechnicalIdResponse();
            response.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createOwner: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in createOwner", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createOwner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Owner by technicalId", description = "Retrieve Owner by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OwnerGetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = "application/json")
    public ResponseEntity<OwnerGetResponse> getOwnerById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            JsonNode dataNode = (JsonNode) dataPayload.getData();
            Owner owner = objectMapper.treeToValue(dataNode, Owner.class);

            OwnerGetResponse response = new OwnerGetResponse();
            response.setTechnicalId(technicalId);
            response.setEntity(owner);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getOwnerById: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in getOwnerById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getOwnerById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Static DTO classes

    @Data
    @Schema(name = "OwnerCreateRequest", description = "Request to create an Owner")
    public static class OwnerCreateRequest {
        @Schema(description = "Business owner id (external)", required = true, example = "external-oid-123")
        private String ownerId;

        @Schema(description = "Full name", required = true, example = "Alice Doe")
        private String name;

        @Schema(description = "Contact information", required = true)
        private ContactInfo contactInfo;

        @Schema(description = "Address (optional)", example = "123 Main St")
        private String address;

        @Schema(description = "Role (user/admin/staff)", example = "user")
        private String role;

        @Schema(description = "Saved pets ids", example = "[\"pet-1\",\"pet-2\"]")
        private List<String> savedPets;

        @Schema(description = "Adopted pets ids", example = "[\"pet-3\"]")
        private List<String> adoptedPets;

        @Schema(description = "Verification status (optional)", example = "unverified")
        private String verificationStatus;
    }

    @Data
    @Schema(name = "ContactInfo", description = "Contact information for owner")
    public static class ContactInfo {
        @Schema(description = "Email address", required = true, example = "alice@example.com")
        private String email;

        @Schema(description = "Phone number", example = "+1-555-0100")
        private String phone;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the persisted entity", example = "owner-technical-6789")
        private String technicalId;
    }

    @Data
    @Schema(name = "OwnerGetResponse", description = "Response for get Owner by technicalId")
    public static class OwnerGetResponse {
        @Schema(description = "Technical id of the entity", example = "owner-technical-6789")
        private String technicalId;

        @Schema(description = "Owner entity")
        private Owner entity;
    }
}