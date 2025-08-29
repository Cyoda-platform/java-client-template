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
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/consents")
@Tag(name = "Consent", description = "APIs for Consent entity (version 1)")
@RequiredArgsConstructor
public class ConsentController {

    private static final Logger logger = LoggerFactory.getLogger(ConsentController.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "Create Consents", description = "Persist multiple Consent entities in batch and start associated workflows.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createConsents(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of Consent create payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateConsentRequest.class))))
            @RequestBody List<CreateConsentRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("request body must be a non-empty array");
            }

            List<Consent> entities = new ArrayList<>();
            for (CreateConsentRequest request : requests) {
                if (request == null) continue;
                Consent entity = new Consent();
                entity.setUser_id(request.getUser_id());
                entity.setType(request.getType());
                entity.setSource(request.getSource());
                entities.add(entity);
            }

            List<UUID> ids = entityService.addItems(
                    Consent.ENTITY_NAME,
                    Consent.ENTITY_VERSION,
                    entities
            ).get();

            List<String> technicalIds = new ArrayList<>();
            if (ids != null) {
                for (UUID u : ids) technicalIds.add(u != null ? u.toString() : null);
            }
            BatchCreateResponse resp = new BatchCreateResponse();
            resp.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createConsents: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during createConsents execution: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during createConsents execution: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createConsents", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating consents", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in createConsents", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

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

    @Data
    @Schema(description = "Batch create response with technicalIds")
    public static class BatchCreateResponse {
        @Schema(description = "List of technical ids of created entities")
        private List<String> technicalIds;
    }
}