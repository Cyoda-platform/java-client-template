package com.java_template.application.controller.ingestionjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/ingestion-job/v1")
@Tag(name = "IngestionJob Controller", description = "APIs for IngestionJob entity (version 1)")
public class IngestionJobController {

    private static final Logger logger = LoggerFactory.getLogger(IngestionJobController.class);

    private final EntityService entityService;

    public IngestionJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Add an IngestionJob", description = "Adds a single IngestionJob entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addIngestionJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "IngestionJob payload",
                    content = @Content(schema = @Schema(implementation = AddIngestionJobRequest.class))
            )
            @RequestBody AddIngestionJobRequest request
    ) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request data must not be null");
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    request.getData()
            );
            UUID id = idFuture.get();

            return ResponseEntity.ok(new IdResponse(id));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request when adding ingestion job: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while adding ingestion job", ee);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding ingestion job", ie);
            return ResponseEntity.status(500).body("Internal Server Error");
        } catch (Exception e) {
            logger.error("Unexpected error while adding ingestion job", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    @Operation(summary = "Add multiple IngestionJobs", description = "Adds multiple IngestionJob entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = UUID.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addIngestionJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "List of IngestionJob payloads",
                    content = @Content(schema = @Schema(implementation = AddIngestionJobsRequest.class))
            )
            @RequestBody AddIngestionJobsRequest request
    ) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request data must not be null");
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    request.getData()
            );
            List<UUID> ids = idsFuture.get();

            return ResponseEntity.ok(new AddIngestionJobsResponse(ids));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request when adding ingestion jobs: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while adding ingestion jobs", ee);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding ingestion jobs", ie);
            return ResponseEntity.status(500).body("Internal Server Error");
        } catch (Exception e) {
            logger.error("Unexpected error while adding ingestion jobs", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    @Operation(summary = "Get an IngestionJob by technicalId", description = "Retrieves a single IngestionJob by technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = GetIngestionJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getIngestionJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId must not be null");
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode item = itemFuture.get();

            return ResponseEntity.ok(new GetIngestionJobResponse(item));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request when getting ingestion job: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while getting ingestion job", ee);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting ingestion job", ie);
            return ResponseEntity.status(500).body("Internal Server Error");
        } catch (Exception e) {
            logger.error("Unexpected error while getting ingestion job", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    @Operation(summary = "Get all IngestionJobs", description = "Retrieves all IngestionJob entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getIngestionJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while getting ingestion jobs", ee);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting ingestion jobs", ie);
            return ResponseEntity.status(500).body("Internal Server Error");
        } catch (Exception e) {
            logger.error("Unexpected error while getting ingestion jobs", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    @Operation(summary = "Search IngestionJobs by condition", description = "Retrieves IngestionJob entities matching the provided search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchIngestionJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Search condition payload",
                    content = @Content(schema = @Schema(implementation = FilterRequest.class))
            )
            @RequestBody FilterRequest request
    ) {
        try {
            if (request == null || request.getCondition() == null) {
                throw new IllegalArgumentException("Search condition must not be null");
            }
            SearchConditionRequest condition = request.getCondition();

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request when searching ingestion jobs: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching ingestion jobs", ee);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching ingestion jobs", ie);
            return ResponseEntity.status(500).body("Internal Server Error");
        } catch (Exception e) {
            logger.error("Unexpected error while searching ingestion jobs", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    @Operation(summary = "Update an IngestionJob", description = "Updates an existing IngestionJob entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateIngestionJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Updated IngestionJob payload",
                    content = @Content(schema = @Schema(implementation = UpdateIngestionJobRequest.class))
            )
            @RequestBody UpdateIngestionJobRequest request
    ) {
        try {
            if (technicalId == null || request == null || request.getData() == null) {
                throw new IllegalArgumentException("technicalId and request data must not be null");
            }

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    request.getData()
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request when updating ingestion job: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating ingestion job", ee);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating ingestion job", ie);
            return ResponseEntity.status(500).body("Internal Server Error");
        } catch (Exception e) {
            logger.error("Unexpected error while updating ingestion job", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    @Operation(summary = "Delete an IngestionJob", description = "Deletes an existing IngestionJob entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteIngestionJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId must not be null");
            }

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    IngestionJob.ENTITY_NAME,
                    String.valueOf(IngestionJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(deletedId));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request when deleting ingestion job: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting ingestion job", ee);
                return ResponseEntity.status(500).body("Internal Server Error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting ingestion job", ie);
            return ResponseEntity.status(500).body("Internal Server Error");
        } catch (Exception e) {
            logger.error("Unexpected error while deleting ingestion job", e);
            return ResponseEntity.status(500).body("Internal Server Error");
        }
    }

    // DTOs

    @Data
    public static class AddIngestionJobRequest {
        @Schema(description = "IngestionJob entity as JSON object", required = true)
        private ObjectNode data;
    }

    @Data
    public static class AddIngestionJobsRequest {
        @Schema(description = "List of IngestionJob entities", required = true)
        private List<ObjectNode> data;
    }

    @Data
    public static class AddIngestionJobsResponse {
        @Schema(description = "List of technical IDs of created entities")
        private List<UUID> ids;

        public AddIngestionJobsResponse(List<UUID> ids) {
            this.ids = ids;
        }
    }

    @Data
    public static class IdResponse {
        @Schema(description = "Technical ID of affected entity")
        private UUID id;

        public IdResponse(UUID id) {
            this.id = id;
        }
    }

    @Data
    public static class GetIngestionJobResponse {
        @Schema(description = "IngestionJob entity as JSON object")
        private ObjectNode data;

        public GetIngestionJobResponse(ObjectNode data) {
            this.data = data;
        }
    }

    @Data
    public static class FilterRequest {
        @Schema(description = "Search condition request for filtering")
        private SearchConditionRequest condition;
    }

    @Data
    public static class UpdateIngestionJobRequest {
        @Schema(description = "Updated IngestionJob entity as JSON object", required = true)
        private ObjectNode data;
    }
}