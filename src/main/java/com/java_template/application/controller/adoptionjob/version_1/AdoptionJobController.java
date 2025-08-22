package com.java_template.application.controller.adoptionjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AdoptionJob", description = "AdoptionJob orchestration endpoints (version 1)")
@RestController
@RequestMapping("/jobs/adoption")
public class AdoptionJobController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdoptionJobController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Adoption Job", description = "Creates an AdoptionJob (status = pending) and starts orchestration. Returns immediate technicalId for tracking.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateAdoptionJobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<CreateAdoptionJobResponse> createAdoptionJob(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Adoption job creation request", required = true,
            content = @Content(schema = @Schema(implementation = CreateAdoptionJobRequest.class)))
        @RequestBody CreateAdoptionJobRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getPetId() == null || request.getPetId().isEmpty()) {
                throw new IllegalArgumentException("petId is required");
            }
            if (request.getUserId() == null || request.getUserId().isEmpty()) {
                throw new IllegalArgumentException("userId is required");
            }
            if (request.getRequestType() == null || request.getRequestType().isEmpty()) {
                throw new IllegalArgumentException("requestType is required");
            }

            ObjectNode data = objectMapper.createObjectNode();
            // Populate entity fields required for creation; business workflows will handle actual logic.
            data.put("petId", request.getPetId());
            data.put("userId", request.getUserId());
            data.put("requestType", request.getRequestType());
            if (request.getNotes() != null) data.put("notes", request.getNotes());
            if (request.getFee() != null) data.put("fee", request.getFee());
            data.put("status", "pending");
            data.put("requestedAt", Instant.now().toString());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                AdoptionJob.ENTITY_NAME,
                String.valueOf(AdoptionJob.ENTITY_VERSION),
                data
            );

            UUID technicalUuid = idFuture.get();
            CreateAdoptionJobResponse response = new CreateAdoptionJobResponse();
            response.setTechnicalId(technicalUuid.toString());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to create adoption job: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException when creating adoption job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating adoption job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error when creating adoption job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Adoption Job by technicalId", description = "Retrieve AdoptionJob domain representation by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetAdoptionJobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<GetAdoptionJobResponse> getAdoptionJobByTechnicalId(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isEmpty()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID uuid = UUID.fromString(technicalId);

            CompletableFuture<com.fasterxml.jackson.databind.node.ObjectNode> itemFuture =
                entityService.getItem(
                    AdoptionJob.ENTITY_NAME,
                    String.valueOf(AdoptionJob.ENTITY_VERSION),
                    uuid
                );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            GetAdoptionJobResponse response = objectMapper.convertValue(node, GetAdoptionJobResponse.class);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to get adoption job: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException when retrieving adoption job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving adoption job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving adoption job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    @Schema(name = "CreateAdoptionJobRequest", description = "Request payload to create an AdoptionJob")
    public static class CreateAdoptionJobRequest {
        @Schema(description = "Target pet id", required = true, example = "pet-123")
        private String petId;

        @Schema(description = "Requesting user id", required = true, example = "user-456")
        private String userId;

        @Schema(description = "Type of request", required = true, example = "adoption")
        private String requestType;

        @Schema(description = "Notes for staff", required = false, example = "Customer prefers morning pickup")
        private String notes;

        @Schema(description = "Adoption fee", required = false, example = "25.0")
        private Double fee;
    }

    @Data
    @Schema(name = "CreateAdoptionJobResponse", description = "Response returned after creating an AdoptionJob")
    public static class CreateAdoptionJobResponse {
        @Schema(description = "Technical id assigned to the AdoptionJob", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "GetAdoptionJobResponse", description = "AdoptionJob representation returned by GET endpoint")
    public static class GetAdoptionJobResponse {
        @Schema(description = "Domain id", example = "job-domain-id")
        private String id;

        @Schema(description = "Target pet id", example = "pet-123")
        private String petId;

        @Schema(description = "Requesting user id", example = "user-456")
        private String userId;

        @Schema(description = "Type of request", example = "adoption")
        private String requestType;

        @Schema(description = "Timestamp when request was created", example = "2025-08-22T12:00:00Z")
        private String requestedAt;

        @Schema(description = "Orchestration status", example = "approved")
        private String status;

        @Schema(description = "Staff/admin who made decision", example = "staff-1")
        private String decisionBy;

        @Schema(description = "Timestamp when processing finished", example = "2025-08-22T13:00:00Z")
        private String processedAt;

        @Schema(description = "Adoption fee", example = "25.0")
        private Double fee;

        @Schema(description = "Notes for staff", example = "Customer prefers morning pickup")
        private String notes;

        @Schema(description = "Result or error details", example = "Adoption completed")
        private String resultDetails;

        @Schema(description = "Technical id (UUID) assigned to the job", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }
}