package com.java_template.application.controller.petsyncjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.petsyncjob.version_1.PetSyncJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.Map;
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

/**
 * Dull proxy controller for PetSyncJob entity. All business logic is executed in workflows.
 */
@RestController
@RequestMapping("/api/v1/jobs/pet-sync")
@Tag(name = "PetSyncJob Controller", description = "Proxy endpoints for PetSyncJob entity")
public class PetSyncJobController {

    private static final Logger logger = LoggerFactory.getLogger(PetSyncJobController.class);
    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public PetSyncJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Start a Pet Sync Job", description = "Create a PetSyncJob entity (starts the orchestration). Returns only technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createJob(@RequestBody PetSyncJobCreateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            ObjectNode data = mapper.valueToTree(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                PetSyncJob.ENTITY_NAME,
                String.valueOf(PetSyncJob.ENTITY_VERSION),
                data
            );
            UUID id = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create PetSyncJob", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating PetSyncJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating PetSyncJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error while creating PetSyncJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Pet Sync Jobs", description = "Bulk create PetSyncJob entities. Returns list of technicalIds.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createJobsBulk(@RequestBody List<PetSyncJobCreateRequest> requests) {
        try {
            if (requests == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            ArrayNode data = mapper.valueToTree(requests);
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                PetSyncJob.ENTITY_NAME,
                String.valueOf(PetSyncJob.ENTITY_VERSION),
                data
            );
            List<UUID> ids = idsFuture.get();
            List<TechnicalIdResponse> resp = ids.stream().map(uuid -> new TechnicalIdResponse(uuid.toString())).toList();
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid bulk request to create PetSyncJob", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while bulk creating PetSyncJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while bulk creating PetSyncJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error while bulk creating PetSyncJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get PetSyncJob by technicalId", description = "Retrieve a PetSyncJob by its technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetSyncJobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getJobById(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                PetSyncJob.ENTITY_NAME,
                String.valueOf(PetSyncJob.ENTITY_VERSION),
                uuid
            );
            ObjectNode node = itemFuture.get();
            PetSyncJobResponse resp = mapper.convertValue(node, PetSyncJobResponse.class);
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getJobById: {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving PetSyncJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving PetSyncJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error while retrieving PetSyncJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List all PetSyncJobs", description = "Retrieve all PetSyncJob entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetSyncJobResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                PetSyncJob.ENTITY_NAME,
                String.valueOf(PetSyncJob.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<PetSyncJobResponse> resp = mapper.convertValue(array, new TypeReference<List<PetSyncJobResponse>>() {});
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while listing PetSyncJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing PetSyncJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error while listing PetSyncJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search PetSyncJobs by condition", description = "Retrieve PetSyncJobs matching a simple search condition")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetSyncJobResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchJobs(@RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition is required");
            }
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                PetSyncJob.ENTITY_NAME,
                String.valueOf(PetSyncJob.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode array = itemsFuture.get();
            List<PetSyncJobResponse> resp = mapper.convertValue(array, new TypeReference<List<PetSyncJobResponse>>() {});
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search condition for PetSyncJob", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching PetSyncJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching PetSyncJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error while searching PetSyncJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update a PetSyncJob", description = "Update a PetSyncJob by technicalId. Returns technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateJob(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable String technicalId,
        @RequestBody PetSyncJobUpdateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            UUID uuid = UUID.fromString(technicalId);
            ObjectNode data = mapper.valueToTree(request);
            CompletableFuture<UUID> updatedId = entityService.updateItem(
                PetSyncJob.ENTITY_NAME,
                String.valueOf(PetSyncJob.ENTITY_VERSION),
                uuid,
                data
            );
            UUID id = updatedId.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to update PetSyncJob: {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating PetSyncJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating PetSyncJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error while updating PetSyncJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete a PetSyncJob", description = "Delete a PetSyncJob by technicalId. Returns technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteJob(
        @Parameter(name = "technicalId", description = "Technical ID of the entity")
        @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                PetSyncJob.ENTITY_NAME,
                String.valueOf(PetSyncJob.ENTITY_VERSION),
                uuid
            );
            UUID id = deletedId.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for deleteJob: {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting PetSyncJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting PetSyncJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Error while deleting PetSyncJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- Static DTO classes ---

    @Data
    @Schema(name = "PetSyncJobCreateRequest", description = "Request to start a PetSyncJob")
    public static class PetSyncJobCreateRequest {
        @Schema(description = "Source of the job (e.g. petstore)", example = "petstore")
        private String source;

        @Schema(description = "Configuration for the job (filters, paging, mapping rules)")
        private Map<String, Object> config;

        @Schema(description = "Optional start time (ISO timestamp)", example = "2025-08-20T10:00:00Z")
        private String start_time;
    }

    @Data
    @Schema(name = "PetSyncJobUpdateRequest", description = "Update payload for PetSyncJob")
    public static class PetSyncJobUpdateRequest {
        @Schema(description = "Job id (domain id)", example = "job_123456")
        private String id;

        @Schema(description = "Source of the job", example = "petstore")
        private String source;

        @Schema(description = "Start time (ISO timestamp)", example = "2025-08-20T10:00:00Z")
        private String start_time;

        @Schema(description = "End time (ISO timestamp)", example = "2025-08-20T10:01:30Z")
        private String end_time;

        @Schema(description = "Status of the job", example = "completed")
        private String status;

        @Schema(description = "Number of fetched items", example = "120")
        private Integer fetched_count;

        @Schema(description = "Error message in case of failure")
        private String error_message;

        @Schema(description = "Job config")
        private Map<String, Object> config;
    }

    @Data
    @Schema(name = "PetSyncJobResponse", description = "Response payload for PetSyncJob retrieval")
    public static class PetSyncJobResponse {
        @Schema(description = "Technical ID of the entity", example = "job_123456")
        private String technicalId;

        @Schema(description = "Domain id / job id", example = "job_123456")
        private String id;

        @Schema(description = "Source of the job", example = "petstore")
        private String source;

        @Schema(description = "Status of the job", example = "completed")
        private String status;

        @Schema(description = "Number of fetched items", example = "120")
        private Integer fetched_count;

        @Schema(description = "Start time (ISO timestamp)", example = "2025-08-20T10:00:00Z")
        private String start_time;

        @Schema(description = "End time (ISO timestamp)", example = "2025-08-20T10:01:30Z")
        private String end_time;

        @Schema(description = "Error message if failed")
        private String error_message;

        @Schema(description = "Config object", implementation = Object.class)
        private Map<String, Object> config;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing a technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity", example = "job_123456")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}