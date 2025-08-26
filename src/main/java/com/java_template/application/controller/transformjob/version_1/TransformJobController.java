package com.java_template.application.controller.transformjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.transformjob.version_1.TransformJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(path = "/api/transform-jobs", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "TransformJob Controller", description = "Controller proxy for TransformJob entity (versioned)")
public class TransformJobController {

    private static final Logger logger = LoggerFactory.getLogger(TransformJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TransformJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create TransformJob", description = "Create a TransformJob entity. Controller proxies request to EntityService and returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "TransformJob creation payload", required = true,
            content = @Content(schema = @Schema(implementation = TransformJobRequest.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createTransformJob(@RequestBody TransformJobRequest request) {
        try {
            // Basic validation (no business logic)
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getJob_type() == null || request.getJob_type().isBlank()) {
                throw new IllegalArgumentException("job_type is required");
            }
            if (request.getCreated_by() == null || request.getCreated_by().isBlank()) {
                throw new IllegalArgumentException("created_by is required");
            }

            // Convert request DTO to ObjectNode to pass to entity service (controller must not contain business logic)
            ObjectNode dataNode = (ObjectNode) objectMapper.valueToTree(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    TransformJob.ENTITY_NAME,
                    String.valueOf(TransformJob.ENTITY_VERSION),
                    dataNode
            );

            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for creating TransformJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating TransformJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating TransformJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while creating TransformJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get TransformJob by technicalId", description = "Retrieve a TransformJob entity by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TransformJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getTransformJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            UUID technicalUuid;
            try {
                technicalUuid = UUID.fromString(technicalId);
            } catch (IllegalArgumentException iae) {
                throw new IllegalArgumentException("technicalId is not a valid UUID: " + iae.getMessage());
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    TransformJob.ENTITY_NAME,
                    String.valueOf(TransformJob.ENTITY_VERSION),
                    technicalUuid
            );

            ObjectNode node = itemFuture.get();

            TransformJobResponse response = objectMapper.treeToValue(node, TransformJobResponse.class);
            // Ensure technicalId is included in response
            response.setTechnicalId(technicalId);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getTransformJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving TransformJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving TransformJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving TransformJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTO classes

    @Data
    @Schema(name = "TransformJobRequest", description = "Payload to create a TransformJob")
    public static class TransformJobRequest {
        @Schema(description = "Job type (search_transform|bulk_transform)", required = true, example = "search_transform")
        private String job_type;

        @Schema(description = "Created by (user or system)", required = true, example = "user-123")
        private String created_by;

        @Schema(description = "Reference to SearchFilter business id", example = "sf-1")
        private String search_filter_id;

        @Schema(description = "Transformation rule names", example = "[\"normalize_age\",\"map_region\"]")
        private List<String> rule_names;

        @Schema(description = "Priority of the job", example = "5")
        private Integer priority;

        @Schema(description = "Optional created_at timestamp", example = "2024-01-01T12:00:00Z")
        private String created_at;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technicalId of created entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity", example = "tjob-789")
        private String technicalId;
    }

    @Data
    @Schema(name = "TransformJobResponse", description = "TransformJob retrieval response")
    public static class TransformJobResponse {
        @Schema(description = "Technical ID of the entity", example = "tjob-789")
        private String technicalId;

        @Schema(description = "Business id", example = "tj-1")
        private String id;

        @Schema(description = "Job type (search_transform|bulk_transform)", example = "search_transform")
        private String job_type;

        @Schema(description = "Created by (user or system)", example = "user-123")
        private String created_by;

        @Schema(description = "Reference to SearchFilter business id", example = "sf-1")
        private String search_filter_id;

        @Schema(description = "Transformation rule names", example = "[\"normalize_age\",\"map_region\"]")
        private List<String> rule_names;

        @Schema(description = "Priority of the job", example = "5")
        private Integer priority;

        @Schema(description = "Status of the job (PENDING|QUEUED|RUNNING|COMPLETED|FAILED)", example = "COMPLETED")
        private String status;

        @Schema(description = "Job start timestamp", example = "2024-01-01T12:01:00Z")
        private String started_at;

        @Schema(description = "Job completion timestamp", example = "2024-01-01T12:10:00Z")
        private String completed_at;

        @Schema(description = "Number of results", example = "12")
        private Integer result_count;

        @Schema(description = "Error message if failed", example = "OutOfMemoryError")
        private String error_message;

        @Schema(description = "Location where output is stored", example = "/results/tj-1.json")
        private String output_location;

        @Schema(description = "Created at timestamp", example = "2024-01-01T12:00:00Z")
        private String created_at;
    }
}