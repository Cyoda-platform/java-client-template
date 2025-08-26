package com.java_template.application.controller.transformjob.version_1;

import static com.java_template.common.config.Config.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.node.ArrayNode;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller proxying requests to the EntityService for TransformJob entity model.
 *
 * Responsibilities:
 * - Validate basic request format
 * - Forward requests to EntityService
 * - Handle ExecutionException unwrapping (NoSuchElementException -> 404, IllegalArgumentException -> 400, others -> 500)
 *
 * Note: all business logic is delegated to the EntityService.
 */
@RestController
@RequestMapping("/api/transform-job/v1")
@Tag(name = "TransformJob", description = "API for TransformJob entity operations (version 1)")
@RequiredArgsConstructor
public class TransformJobController {

    private static final Logger logger = LoggerFactory.getLogger(TransformJobController.class);

    private final EntityService entityService;

    @Operation(summary = "Add TransformJob", description = "Adds a single TransformJob entity and returns the created technical id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddTransformJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> addTransformJob(@RequestBody AddTransformJobRequest request) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request body or data cannot be null");
            }
            TransformJob data = request.getData();
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    TransformJob.ENTITY_NAME,
                    String.valueOf(TransformJob.ENTITY_VERSION),
                    data
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new AddTransformJobResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to addTransformJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while adding TransformJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding TransformJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while adding TransformJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Add multiple TransformJobs", description = "Adds multiple TransformJob entities and returns their created technical ids.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddTransformJobsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> addTransformJobs(@RequestBody AddTransformJobsRequest request) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request body or data cannot be null");
            }
            List<TransformJob> data = request.getData();
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    TransformJob.ENTITY_NAME,
                    String.valueOf(TransformJob.ENTITY_VERSION),
                    data
            );
            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new AddTransformJobsResponse(ids));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to addTransformJobs: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while adding TransformJobs", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding TransformJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while adding TransformJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get TransformJob by technicalId", description = "Retrieves a single TransformJob by its technical id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetTransformJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getTransformJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    TransformJob.ENTITY_NAME,
                    String.valueOf(TransformJob.ENTITY_VERSION),
                    id
            );
            ObjectNode result = itemFuture.get();
            return ResponseEntity.ok(new GetTransformJobResponse(result));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for getTransformJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while getting TransformJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting TransformJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while getting TransformJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all TransformJobs", description = "Retrieves all TransformJob entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = GetTransformJobsResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getTransformJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    TransformJob.ENTITY_NAME,
                    String.valueOf(TransformJob.ENTITY_VERSION)
            );
            ArrayNode result = itemsFuture.get();
            return ResponseEntity.ok(new GetTransformJobsResponse(result));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while getting TransformJobs", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting TransformJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while getting TransformJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Find TransformJobs by condition", description = "Retrieves TransformJob entities matching the provided search condition.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetTransformJobsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> findTransformJobsByCondition(@RequestBody SearchConditionRequest conditionRequest) {
        try {
            if (conditionRequest == null) {
                throw new IllegalArgumentException("Search condition cannot be null");
            }
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    TransformJob.ENTITY_NAME,
                    String.valueOf(TransformJob.ENTITY_VERSION),
                    conditionRequest,
                    true
            );
            ArrayNode result = filteredItemsFuture.get();
            return ResponseEntity.ok(new GetTransformJobsResponse(result));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search condition for findTransformJobsByCondition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching TransformJobs", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching TransformJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while searching TransformJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update TransformJob", description = "Updates an existing TransformJob identified by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateTransformJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateTransformJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @RequestBody UpdateTransformJobRequest request) {
        try {
            if (request == null || request.getData() == null) {
                throw new IllegalArgumentException("Request body or data cannot be null");
            }
            UUID id = UUID.fromString(technicalId);
            TransformJob data = request.getData();
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    TransformJob.ENTITY_NAME,
                    String.valueOf(TransformJob.ENTITY_VERSION),
                    id,
                    data
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new UpdateTransformJobResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to updateTransformJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating TransformJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating TransformJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while updating TransformJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete TransformJob", description = "Deletes a TransformJob identified by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteTransformJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteTransformJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    TransformJob.ENTITY_NAME,
                    String.valueOf(TransformJob.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new DeleteTransformJobResponse(deletedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for deleteTransformJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting TransformJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting TransformJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while deleting TransformJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "AddTransformJobRequest", description = "Request to add a TransformJob")
    public static class AddTransformJobRequest {
        @Schema(description = "TransformJob entity to add", required = true)
        private TransformJob data;
    }

    @Data
    @Schema(name = "AddTransformJobResponse", description = "Response after adding a TransformJob")
    public static class AddTransformJobResponse {
        @Schema(description = "Created technical id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public AddTransformJobResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "AddTransformJobsRequest", description = "Request to add multiple TransformJobs")
    public static class AddTransformJobsRequest {
        @Schema(description = "List of TransformJob entities to add", required = true)
        private List<TransformJob> data;
    }

    @Data
    @Schema(name = "AddTransformJobsResponse", description = "Response after adding multiple TransformJobs")
    public static class AddTransformJobsResponse {
        @Schema(description = "Created technical ids")
        private List<UUID> technicalIds;

        public AddTransformJobsResponse(List<UUID> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "GetTransformJobResponse", description = "Response returning a single TransformJob")
    public static class GetTransformJobResponse {
        @Schema(description = "Raw JSON representing the entity")
        private ObjectNode data;

        public GetTransformJobResponse(ObjectNode data) {
            this.data = data;
        }
    }

    @Data
    @Schema(name = "GetTransformJobsResponse", description = "Response returning multiple TransformJobs")
    public static class GetTransformJobsResponse {
        @Schema(description = "Raw JSON array representing entities")
        private ArrayNode data;

        public GetTransformJobsResponse(ArrayNode data) {
            this.data = data;
        }
    }

    @Data
    @Schema(name = "UpdateTransformJobRequest", description = "Request to update a TransformJob")
    public static class UpdateTransformJobRequest {
        @Schema(description = "TransformJob entity with updated values", required = true)
        private TransformJob data;
    }

    @Data
    @Schema(name = "UpdateTransformJobResponse", description = "Response after updating a TransformJob")
    public static class UpdateTransformJobResponse {
        @Schema(description = "Updated technical id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public UpdateTransformJobResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "DeleteTransformJobResponse", description = "Response after deleting a TransformJob")
    public static class DeleteTransformJobResponse {
        @Schema(description = "Deleted technical id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public DeleteTransformJobResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}