package com.java_template.application.controller.adoptionrequest.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/adoptionRequests")
@Tag(name = "AdoptionRequest", description = "AdoptionRequest entity proxy controller (version 1)")
public class AdoptionRequestController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdoptionRequestController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create AdoptionRequest", description = "Persist an AdoptionRequest entity and start its workflow. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createAdoptionRequest(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "AdoptionRequest creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = AdoptionRequestCreateRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody AdoptionRequestCreateRequest request) {
        try {
            if (request == null) {
                logger.warn("Received null request body for createAdoptionRequest");
                return ResponseEntity.badRequest().build();
            }

            // Map request DTO to entity (no business logic)
            AdoptionRequest entity = new AdoptionRequest();
            entity.setRequestId(request.getRequestId());
            entity.setPetId(request.getPetId());
            entity.setUserId(request.getUserId());
            entity.setHomeVisitRequired(request.getHomeVisitRequired());
            entity.setAdoptionFee(request.getAdoptionFee());
            entity.setNotes(request.getNotes());
            entity.setPaymentStatus(request.getPaymentStatus());
            entity.setStatus(request.getStatus());
            entity.setRequestedAt(request.getRequestedAt());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    AdoptionRequest.ENTITY_NAME,
                    AdoptionRequest.ENTITY_VERSION,
                    entity
            );
            UUID technicalId = idFuture.get();

            TechnicalIdResponse response = new TechnicalIdResponse();
            response.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("IllegalArgumentException in createAdoptionRequest: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in createAdoptionRequest", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in createAdoptionRequest", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in createAdoptionRequest", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get AdoptionRequest by technicalId", description = "Retrieve a persisted AdoptionRequest by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = AdoptionRequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getAdoptionRequestById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                logger.warn("Empty technicalId provided to getAdoptionRequestById");
                return ResponseEntity.badRequest().build();
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            JsonNode dataNode = dataPayload.getData();
            AdoptionRequestResponse response;
            if (dataNode != null && !dataNode.isNull()) {
                response = objectMapper.treeToValue(dataNode, AdoptionRequestResponse.class);
            } else {
                // If no data node, return minimal response
                response = new AdoptionRequestResponse();
            }

            // Extract technicalId from meta if present
            JsonNode metaNode = dataPayload.getMeta();
            if (metaNode != null && metaNode.has("entityId")) {
                response.setTechnicalId(metaNode.get("entityId").asText());
            } else {
                response.setTechnicalId(technicalId);
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("IllegalArgumentException in getAdoptionRequestById: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in getAdoptionRequestById", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("InterruptedException in getAdoptionRequestById", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled exception in getAdoptionRequestById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Static DTOs for request/response with Swagger Schemas
    // Implemented without Lombok to avoid relying on annotation processing in controller-level DTOs

    @Schema(name = "AdoptionRequestCreateRequest", description = "Payload to create an AdoptionRequest")
    public static class AdoptionRequestCreateRequest {
        @Schema(description = "Business request id", example = "r_1001", required = true)
        private String requestId;

        @Schema(description = "Target pet external id", example = "ext-123", required = true)
        private String petId;

        @Schema(description = "Requester user id", example = "u_001", required = true)
        private String userId;

        @Schema(description = "ISO timestamp when requested", example = "2025-08-28T12:00:00Z", required = true)
        private String requestedAt;

        @Schema(description = "Is a home visit required", example = "true", required = false)
        private Boolean homeVisitRequired;

        @Schema(description = "Adoption fee", example = "50.0", required = false)
        private Double adoptionFee;

        @Schema(description = "Payment status (NOT_PAID/PENDING/PAID)", example = "NOT_PAID", required = false)
        private String paymentStatus;

        @Schema(description = "Request status (CREATED/...)", example = "CREATED", required = false)
        private String status;

        @Schema(description = "Additional notes", example = "Requester prefers evening visits", required = false)
        private String notes;

        public AdoptionRequestCreateRequest() {}

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

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

        public String getRequestedAt() {
            return requestedAt;
        }

        public void setRequestedAt(String requestedAt) {
            this.requestedAt = requestedAt;
        }

        public Boolean getHomeVisitRequired() {
            return homeVisitRequired;
        }

        public void setHomeVisitRequired(Boolean homeVisitRequired) {
            this.homeVisitRequired = homeVisitRequired;
        }

        public Double getAdoptionFee() {
            return adoptionFee;
        }

        public void setAdoptionFee(Double adoptionFee) {
            this.adoptionFee = adoptionFee;
        }

        public String getPaymentStatus() {
            return paymentStatus;
        }

        public void setPaymentStatus(String paymentStatus) {
            this.paymentStatus = paymentStatus;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }

    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the persisted entity", example = "req_mno456")
        private String technicalId;

        public TechnicalIdResponse() {}

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Schema(name = "AdoptionRequestResponse", description = "AdoptionRequest returned from storage")
    public static class AdoptionRequestResponse {
        @Schema(description = "Technical id of the entity", example = "req_mno456")
        private String technicalId;

        @Schema(description = "Business request id", example = "r_1001")
        private String requestId;

        @Schema(description = "Target pet external id", example = "ext-123")
        private String petId;

        @Schema(description = "Requester user id", example = "u_001")
        private String userId;

        @Schema(description = "ISO timestamp when requested", example = "2025-08-28T12:00:00Z")
        private String requestedAt;

        @Schema(description = "Is a home visit required", example = "true")
        private Boolean homeVisitRequired;

        @Schema(description = "Adoption fee", example = "50.0")
        private Double adoptionFee;

        @Schema(description = "Payment status (NOT_PAID/PENDING/PAID)", example = "NOT_PAID")
        private String paymentStatus;

        @Schema(description = "Request status (CREATED/PENDING_REVIEW/...)", example = "CREATED")
        private String status;

        @Schema(description = "Additional notes", example = "Requester prefers evening visits")
        private String notes;

        public AdoptionRequestResponse() {}

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

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

        public String getRequestedAt() {
            return requestedAt;
        }

        public void setRequestedAt(String requestedAt) {
            this.requestedAt = requestedAt;
        }

        public Boolean getHomeVisitRequired() {
            return homeVisitRequired;
        }

        public void setHomeVisitRequired(Boolean homeVisitRequired) {
            this.homeVisitRequired = homeVisitRequired;
        }

        public Double getAdoptionFee() {
            return adoptionFee;
        }

        public void setAdoptionFee(Double adoptionFee) {
            this.adoptionFee = adoptionFee;
        }

        public String getPaymentStatus() {
            return paymentStatus;
        }

        public void setPaymentStatus(String paymentStatus) {
            this.paymentStatus = paymentStatus;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}