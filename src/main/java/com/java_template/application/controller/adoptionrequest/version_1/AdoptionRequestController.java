package com.java_template.application.controller.adoptionrequest.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Instant;

@RestController
@RequestMapping("/adoption-requests")
@Tag(name = "AdoptionRequest", description = "AdoptionRequest entity endpoints (proxy to EntityService)")
public class AdoptionRequestController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdoptionRequestController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create AdoptionRequest", description = "Create an AdoptionRequest entity (emits event). Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createAdoptionRequest(
            @RequestBody(description = "Adoption request payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateAdoptionRequestDto.class)))
            @Valid @RequestBody CreateAdoptionRequestDto requestBody) {
        try {
            if (requestBody == null) {
                throw new IllegalArgumentException("Request body is null");
            }

            AdoptionRequest entity = new AdoptionRequest();
            entity.setPetId(requestBody.getPetId());
            entity.setRequesterName(requestBody.getRequesterName());
            if (requestBody.getContactInfo() != null) {
                entity.setContactEmail(requestBody.getContactInfo().getEmail());
                entity.setContactPhone(requestBody.getContactInfo().getPhone());
            }
            entity.setMotivation(requestBody.getMotivation());
            // Minimal technical fields set to mark creation event - workflows will implement business logic
            entity.setStatus("created");
            entity.setSubmittedAt(Instant.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    AdoptionRequest.ENTITY_NAME,
                    AdoptionRequest.ENTITY_VERSION,
                    entity
            );
            UUID entityId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for creating AdoptionRequest: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while creating AdoptionRequest", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating AdoptionRequest", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error while creating AdoptionRequest", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get AdoptionRequest by technicalId", description = "Retrieve an AdoptionRequest by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AdoptionRequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<AdoptionRequestResponse> getAdoptionRequestById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            JsonNode node = dataPayload != null ? (JsonNode) dataPayload.getData() : null;
            if (node == null || node.isNull()) {
                return ResponseEntity.notFound().build();
            }

            AdoptionRequest entity = objectMapper.treeToValue(node, AdoptionRequest.class);

            AdoptionRequestResponse resp = new AdoptionRequestResponse();
            resp.setTechnicalId(entity.getId());
            resp.setPetId(entity.getPetId());
            resp.setRequesterName(entity.getRequesterName());
            AdoptionRequestResponse.ContactInfoDto contact = new AdoptionRequestResponse.ContactInfoDto();
            contact.setEmail(entity.getContactEmail());
            contact.setPhone(entity.getContactPhone());
            resp.setContactInfo(contact);
            resp.setMotivation(entity.getMotivation());
            resp.setNotes(entity.getNotes());
            resp.setProcessedBy(entity.getProcessedBy());
            resp.setStatus(entity.getStatus());
            resp.setSubmittedAt(entity.getSubmittedAt());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getAdoptionRequestById: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while retrieving AdoptionRequest", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving AdoptionRequest", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving AdoptionRequest", ex);
            return ResponseEntity.status(500).build();
        }
    }

    // Request/Response DTOs

    @Data
    @Schema(name = "CreateAdoptionRequest", description = "Payload to create an AdoptionRequest")
    public static class CreateAdoptionRequestDto {
        @NotBlank
        @Schema(description = "Reference to pet (technical id or UUID)", required = true)
        private String petId;

        @NotBlank
        @Schema(description = "Name of the requester", required = true)
        private String requesterName;

        @Schema(description = "Contact information")
        private ContactInfoDto contactInfo;

        @Schema(description = "Motivation text")
        private String motivation;
    }

    @Data
    @Schema(name = "ContactInfo", description = "Contact information payload")
    public static class ContactInfoDto {
        @Schema(description = "Email address of the requester")
        private String email;

        @Schema(description = "Phone number of the requester")
        private String phone;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of created entity")
        private String technicalId;
    }

    @Data
    @Schema(name = "AdoptionRequestResponse", description = "AdoptionRequest response payload")
    public static class AdoptionRequestResponse {
        @Schema(description = "Technical id of the adoption request")
        private String technicalId;

        @Schema(description = "Reference to pet")
        private String petId;

        @Schema(description = "Name of the requester")
        private String requesterName;

        @Schema(description = "Contact information")
        private ContactInfoDto contactInfo;

        @Schema(description = "Motivation text")
        private String motivation;

        @Schema(description = "Processing notes")
        private String notes;

        @Schema(description = "User who processed the request")
        private String processedBy;

        @Schema(description = "Status of the request")
        private String status;

        @Schema(description = "Submitted at timestamp (ISO-8601)")
        private String submittedAt;

        @Data
        @Schema(name = "ContactInfo", description = "Contact information payload")
        public static class ContactInfoDto {
            @Schema(description = "Email address of the requester")
            private String email;

            @Schema(description = "Phone number of the requester")
            private String phone;
        }
    }
}