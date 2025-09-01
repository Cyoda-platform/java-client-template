package com.java_template.application.controller.adoptionjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.List;

@RestController
@RequestMapping("/api/adoptionjob/v1")
@Tag(name = "AdoptionJob", description = "Controller for AdoptionJob entity (proxy to EntityService)")
public class AdoptionJobController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AdoptionJobController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Adoption Match Job", description = "Starts an adoption match orchestration job. Returns the technicalId (UUID) of the created job.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/jobs/adoption-match", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> createAdoptionMatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Adoption match request", required = true,
                    content = @Content(schema = @Schema(implementation = AdoptionMatchRequest.class)))
            @RequestBody AdoptionMatchRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getOwnerId() == null || request.getOwnerId().isBlank()) {
                throw new IllegalArgumentException("ownerId is required");
            }
            if (request.getCriteria() == null || request.getCriteria().isBlank()) {
                throw new IllegalArgumentException("criteria is required");
            }

            AdoptionJob entity = new AdoptionJob();
            // minimal technical plumbing: generate id and timestamp; workflow processors will handle business logic
            entity.setId(UUID.randomUUID().toString());
            entity.setOwnerId(request.getOwnerId());
            entity.setCriteria(request.getCriteria());
            entity.setStatus("PENDING");
            entity.setCreatedAt(Instant.now().toString());
            // resultCount and resultsPreview default values are fine

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    AdoptionJob.ENTITY_NAME,
                    AdoptionJob.ENTITY_VERSION,
                    entity
            );

            UUID createdId = idFuture.get();
            return ResponseEntity.ok(createdId.toString());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to create adoption match: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during create: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during create: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating adoption match", e);
                String msg = cause != null ? cause.getMessage() : e.getMessage();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(msg);
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating adoption match", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Adoption Job", description = "Retrieve AdoptionJob entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AdoptionJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/jobs/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAdoptionJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionJob not found");
            }

            JsonNode dataNode = (JsonNode) dataPayload.getData();
            AdoptionJobResponse response = objectMapper.treeToValue(dataNode, AdoptionJobResponse.class);

            // Ensure id present: try meta
            try {
                JsonNode meta = dataPayload.getMeta();
                if ((response.getId() == null || response.getId().isBlank()) && meta != null && meta.has("entityId")) {
                    response.setId(meta.get("entityId").asText());
                }
            } catch (Exception ignore) {
                // ignore meta extraction problems; not business logic
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to get adoption job: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("AdoptionJob not found: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during get: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving adoption job", e);
                String msg = cause != null ? cause.getMessage() : e.getMessage();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(msg);
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving adoption job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "AdoptionMatchRequest", description = "Request to start an adoption match job")
    public static class AdoptionMatchRequest {
        @Schema(description = "Owner id (business id or UUID string)", required = true, example = "owner-789")
        private String ownerId;

        @Schema(description = "Criteria JSON string", required = true, example = "{\"species\":\"cat\",\"ageMax\":3}")
        private String criteria;
    }

    @Data
    @Schema(name = "AdoptionJobResponse", description = "AdoptionJob response payload")
    public static class AdoptionJobResponse {
        @Schema(description = "Technical id of the job", example = "b3b1a1c2-...") 
        private String id;

        @Schema(description = "Owner id", example = "owner-789")
        private String ownerId;

        @Schema(description = "Criteria JSON", example = "{\"species\":\"cat\",\"ageMax\":3}")
        private String criteria;

        @Schema(description = "Job status", example = "COMPLETED")
        private String status;

        @Schema(description = "Creation timestamp (ISO-8601)")
        private String createdAt;

        @Schema(description = "Number of matched pets")
        private Integer resultCount;

        @Schema(description = "Preview of matched pet ids")
        private List<String> resultsPreview;
    }
}