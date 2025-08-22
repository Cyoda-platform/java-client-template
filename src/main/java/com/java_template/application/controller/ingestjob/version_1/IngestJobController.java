package com.java_template.application.controller.ingestjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.ingestjob.version_1.IngestJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/ingest-job/v1")
@Tag(name = "IngestJob", description = "Operations for IngestJob entity (v1)")
@RequiredArgsConstructor
public class IngestJobController {

    private static final Logger logger = LoggerFactory.getLogger(IngestJobController.class);

    private final EntityService entityService;

    @Operation(summary = "Create IngestJob", description = "Create a single IngestJob entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createIngestJob(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "IngestJob creation payload",
            required = true,
            content = @Content(schema = @Schema(implementation = IngestJobCreateRequest.class))
    ) @RequestBody IngestJobCreateRequest request) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request data must not be null");
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    IngestJob.ENTITY_NAME,
                    String.valueOf(IngestJob.ENTITY_VERSION),
                    request.getData()
            );

            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createIngestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createIngestJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createIngestJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error during createIngestJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Create multiple IngestJobs", description = "Create multiple IngestJob entities in batch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createIngestJobsBatch(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Batch creation payload",
            required = true,
            content = @Content(schema = @Schema(implementation = IngestJobBatchCreateRequest.class))
    ) @RequestBody IngestJobBatchCreateRequest request) {
        try {
            if (request == null || request.getData() == null || request.getData().isEmpty()) {
                throw new IllegalArgumentException("Request data must not be null or empty");
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    IngestJob.ENTITY_NAME,
                    String.valueOf(IngestJob.ENTITY_VERSION),
                    request.getData()
            );

            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new IdsResponse(ids));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createIngestJobsBatch: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createIngestJobsBatch", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createIngestJobsBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error during createIngestJobsBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Get IngestJob by technicalId", description = "Retrieve a single IngestJob by technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getIngestJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    IngestJob.ENTITY_NAME,
                    String.valueOf(IngestJob.ENTITY_VERSION),
                    id
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for getIngestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getIngestJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getIngestJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error during getIngestJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Get all IngestJobs", description = "Retrieve all IngestJob entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllIngestJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    IngestJob.ENTITY_NAME,
                    String.valueOf(IngestJob.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getAllIngestJobs", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getAllIngestJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error during getAllIngestJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Search IngestJobs by condition", description = "Retrieve IngestJob entities matching a simple search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchIngestJobsByCondition(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Search condition payload",
            required = true,
            content = @Content(schema = @Schema(implementation = SearchRequest.class))
    ) @RequestBody SearchRequest request) {
        try {
            if (request == null || request.getCondition() == null) {
                throw new IllegalArgumentException("Search condition must not be null");
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    IngestJob.ENTITY_NAME,
                    String.valueOf(IngestJob.ENTITY_VERSION),
                    request.getCondition(),
                    true
            );

            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search request for searchIngestJobsByCondition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during searchIngestJobsByCondition", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during searchIngestJobsByCondition", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error during searchIngestJobsByCondition", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Update IngestJob", description = "Update an existing IngestJob by technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateIngestJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Update payload",
                    required = true,
                    content = @Content(schema = @Schema(implementation = IngestJobUpdateRequest.class))
            ) @RequestBody IngestJobUpdateRequest request) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request data must not be null");
            }

            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    IngestJob.ENTITY_NAME,
                    String.valueOf(IngestJob.ENTITY_VERSION),
                    id,
                    request.getData()
            );

            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for updateIngestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during updateIngestJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during updateIngestJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error during updateIngestJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Delete IngestJob", description = "Delete an IngestJob by technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteIngestJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    IngestJob.ENTITY_NAME,
                    String.valueOf(IngestJob.ENTITY_VERSION),
                    id
            );

            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(deletedId));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for deleteIngestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during deleteIngestJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during deleteIngestJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error during deleteIngestJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    // DTOs

    @Data
    public static class IngestJobCreateRequest {
        @Schema(description = "IngestJob entity data as JSON object", required = true, implementation = Object.class)
        private ObjectNode data;
    }

    @Data
    public static class IngestJobBatchCreateRequest {
        @Schema(description = "List of IngestJob entity data objects", required = true)
        private List<ObjectNode> data;
    }

    @Data
    public static class IngestJobUpdateRequest {
        @Schema(description = "Updated IngestJob entity data as JSON object", required = true, implementation = Object.class)
        private ObjectNode data;
    }

    @Data
    public static class SearchRequest {
        @Schema(description = "Search condition request", required = true, implementation = SearchConditionRequest.class)
        private SearchConditionRequest condition;
    }

    @Data
    public static class IdResponse {
        @Schema(description = "Technical id of the entity", required = true)
        private UUID id;

        public IdResponse(UUID id) {
            this.id = id;
        }
    }

    @Data
    public static class IdsResponse {
        @Schema(description = "List of technical ids", required = true)
        private List<UUID> ids;

        public IdsResponse(List<UUID> ids) {
            this.ids = ids;
        }
    }
}