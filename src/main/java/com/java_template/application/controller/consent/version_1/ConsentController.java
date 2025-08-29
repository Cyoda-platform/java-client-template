package com.java_template.application.controller.consent.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.consent.version_1.Consent;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/consents")
@Tag(name = "Consent", description = "APIs for Consent entity (version 1)")
@RequiredArgsConstructor
public class ConsentController {

    private static final Logger logger = LoggerFactory.getLogger(ConsentController.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "Create Consent", description = "Persist a new Consent entity and start associated workflows.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createConsent(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Consent create request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateConsentRequest.class)))
            @RequestBody CreateConsentRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Consent entity = new Consent();
            // Map incoming simple fields. Business rules (like generating IDs, timestamps, statuses) are handled in workflows.
            entity.setUser_id(request.getUser_id());
            entity.setType(request.getType());
            entity.setSource(request.getSource());
            // Optional fields from request can be added here if present in future.

            UUID createdId = entityService.addItem(
                    Consent.ENTITY_NAME,
                    Consent.ENTITY_VERSION,
                    entity
            ).get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(createdId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createConsent: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during createConsent execution: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during createConsent execution: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createConsent", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating consent", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in createConsent", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Consent by technicalId", description = "Retrieve a persisted Consent entity by its technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ConsentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getConsentById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId path parameter is required");
            }

            UUID id = UUID.fromString(technicalId);

            DataPayload dataPayload = entityService.getItem(id).get();
            if (dataPayload == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            ObjectNode node = dataPayload.getData() != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            ConsentResponse response = objectMapper.treeToValue(node, ConsentResponse.class);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getConsentById: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Consent not found: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during getConsentById execution: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getConsentById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving consent", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in getConsentById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Request/Response DTOs

    @Data
    @Schema(name = "CreateConsentRequest", description = "Request payload to create a Consent")
    public static class CreateConsentRequest {
        @Schema(description = "User id who the consent is for", example = "user-123", required = true)
        private String user_id;

        @Schema(description = "Type of consent (marketing/analytics)", example = "marketing", required = true)
        private String type;

        @Schema(description = "Source of the consent request", example = "signup_form", required = false)
        private String source;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technicalId of the created entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the created entity", example = "tech-consent-001")
        private String technicalId;
    }

    @Data
    @Schema(name = "ConsentResponse", description = "Full Consent entity representation")
    public static class ConsentResponse {
        @Schema(description = "Consent id", example = "consent-123")
        private String consent_id;

        @Schema(description = "User id related to consent", example = "user-123")
        private String user_id;

        @Schema(description = "Timestamp when requested (ISO)", example = "2025-08-29T12:34:56Z")
        private String requested_at;

        @Schema(description = "Consent status", example = "active")
        private String status;

        @Schema(description = "Type of consent", example = "marketing")
        private String type;

        @Schema(description = "Evidence reference", example = "evidence-456")
        private String evidence_ref;

        @Schema(description = "Timestamp when granted (ISO)", example = "2025-08-29T12:35:56Z")
        private String granted_at;

        @Schema(description = "Timestamp when revoked (ISO)", example = "2025-09-01T09:00:00Z")
        private String revoked_at;

        @Schema(description = "Source of the consent", example = "signup_form")
        private String source;
    }
}