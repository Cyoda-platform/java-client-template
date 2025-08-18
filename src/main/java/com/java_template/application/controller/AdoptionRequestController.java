package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/adoptionRequests")
@Tag(name = "AdoptionRequestController")
public class AdoptionRequestController {
    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);

    private final EntityService entityService;

    public AdoptionRequestController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create adoption request", description = "Create a new adoption request. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createRequest(@RequestBody(description = "AdoptionRequest create payload", required = true,
            content = @Content(schema = @Schema(implementation = CreateAdoptionRequest.class))) CreateAdoptionRequest request) {
        try {
            AdoptionRequest ar = new AdoptionRequest();
            ar.setPetId(request.getPetTechnicalId());
            ar.setUserId(request.getUserTechnicalId());
            ar.setStatus("SUBMITTED");
            ar.setSubmittedAt(request.getSubmittedAt());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    ar
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id.toString()));
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument when creating adoption request", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception when creating adoption request", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating adoption request", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error when creating adoption request", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get adoption request by technicalId", description = "Retrieve a persisted AdoptionRequest by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getRequest(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    uuid
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument when getting adoption request", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception when getting adoption request", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when getting adoption request", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error when getting adoption request", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Approve adoption request", description = "Approve an adoption request (staff action)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/{technicalId}/approve", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> approveRequest(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            // This controller is a dumb proxy: update status to APPROVED
            AdoptionRequest ar = new AdoptionRequest();
            // Only status change; consumer processors will handle validation
            ar.setStatus("APPROVED");
            CompletableFuture<java.util.UUID> updated = entityService.updateItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    uuid,
                    ar
            );
            UUID res = updated.get();
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument when approving adoption request", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception when approving adoption request", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when approving adoption request", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error when approving adoption request", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Schedule pickup for adoption request", description = "Schedule pickup (creates reservation and sets request to SCHEDULED). Returns 200 or 409 if conflict.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "409", description = "Conflict"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/{technicalId}/schedule", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> schedulePickup(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
            @RequestBody(description = "Schedule payload", required = true, content = @Content(schema = @Schema(implementation = ScheduleRequest.class))) ScheduleRequest request
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            // Dumb proxy: set scheduledPickup and status=SCHEDULED
            AdoptionRequest ar = new AdoptionRequest();
            ar.setScheduledPickup(request.getProposedPickupAt());
            ar.setStatus("SCHEDULED");
            CompletableFuture<java.util.UUID> updated = entityService.updateItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    uuid,
                    ar
            );
            updated.get();
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument when scheduling adoption request", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception when scheduling adoption request", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when scheduling adoption request", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error when scheduling adoption request", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Confirm pickup for adoption request", description = "Confirm pickup and finalize adoption. Marks pet as ADOPTED and request as COMPLETED.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/{technicalId}/confirm-pickup", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> confirmPickup(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            // Dumb proxy: set status=COMPLETED on adoption request. Pet update should be handled by processors.
            AdoptionRequest ar = new AdoptionRequest();
            ar.setStatus("COMPLETED");
            CompletableFuture<java.util.UUID> updated = entityService.updateItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    uuid,
                    ar
            );
            updated.get();
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument when confirming pickup", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception when confirming pickup", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when confirming pickup", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error when confirming pickup", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "CreateAdoptionRequest")
    public static class CreateAdoptionRequest {
        private String petTechnicalId;
        private String userTechnicalId;
        private String submittedAt;
    }

    @Data
    @Schema(name = "ScheduleRequest")
    public static class ScheduleRequest {
        private String proposedPickupAt;
    }

    @Data
    @Schema(name = "IdResponse")
    public static class IdResponse {
        private final String technicalId;

        public IdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
