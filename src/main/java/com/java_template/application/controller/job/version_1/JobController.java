package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.job.version_1.Job;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/job/v1")
@Tag(name = "Job Controller", description = "APIs to manage Job entities (v1)")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Add Job", description = "Add a single Job entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> addJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job create request")
            @RequestBody CreateJobRequest request) {
        try {
            if (request == null || request.getJob() == null) {
                throw new IllegalArgumentException("Request body must contain job");
            }

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    request.getJob()
            );

            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for addJob", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during addJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in addJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Add multiple Jobs", description = "Add multiple Job entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/batch")
    public ResponseEntity<?> addJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Jobs create request")
            @RequestBody CreateJobsRequest request) {
        try {
            if (request == null || request.getJobs() == null) {
                throw new IllegalArgumentException("Request body must contain jobs list");
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    request.getJobs()
            );

            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new IdsResponse(ids));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for addJobs", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during addJobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in addJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Jobs", description = "Retrieve all Job entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobsResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> getJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION)
            );

            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(new JobsResponse(array));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during getJobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Jobs by condition", description = "Retrieve Jobs filtered by a search condition (in-memory)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobsResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request")
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition must be provided");
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(new JobsResponse(array));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for searchJobs", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during searchJobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in searchJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Job by id", description = "Retrieve a Job entity by its technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = JobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(new JobResponse(node));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getJob", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during getJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Job", description = "Update a Job entity by its technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job update request")
            @RequestBody CreateJobRequest request) {
        try {
            if (request == null || request.getJob() == null) {
                throw new IllegalArgumentException("Request body must contain job");
            }

            UUID id = UUID.fromString(technicalId);

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    id,
                    request.getJob()
            );

            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateJob", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during updateJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in updateJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Job", description = "Delete a Job entity by its technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    id
            );

            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(deletedId));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for deleteJob", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during deleteJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in deleteJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ResponseEntity<?> handleExecutionException(Throwable cause) {
        if (cause == null) {
            logger.error("ExecutionException with null cause");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unknown error");
        }
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found", cause);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument in async operation", cause);
            return ResponseEntity.badRequest().body(cause.getMessage());
        } else {
            logger.error("ExecutionException with unexpected cause", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        }
    }

    // DTOs

    @Data
    @Schema(name = "CreateJobRequest", description = "Request to create or update a Job")
    public static class CreateJobRequest {
        @Schema(description = "Job entity", implementation = Job.class)
        private Job job;
    }

    @Data
    @Schema(name = "CreateJobsRequest", description = "Request to create multiple Jobs")
    public static class CreateJobsRequest {
        @Schema(description = "List of Job entities", implementation = Job.class)
        private List<Job> jobs;
    }

    @Data
    @Schema(name = "IdResponse", description = "Response containing a single technical id")
    public static class IdResponse {
        @Schema(description = "Technical id")
        private UUID id;

        public IdResponse(UUID id) {
            this.id = id;
        }
    }

    @Data
    @Schema(name = "IdsResponse", description = "Response containing list of technical ids")
    public static class IdsResponse {
        @Schema(description = "List of technical ids")
        private List<UUID> ids;

        public IdsResponse(List<UUID> ids) {
            this.ids = ids;
        }
    }

    @Data
    @Schema(name = "JobResponse", description = "Response containing a job")
    public static class JobResponse {
        @Schema(description = "Job data", implementation = ObjectNode.class)
        private JsonNode data;

        public JobResponse(JsonNode data) {
            this.data = data;
        }
    }

    @Data
    @Schema(name = "JobsResponse", description = "Response containing jobs array")
    public static class JobsResponse {
        @Schema(description = "Array of job items", implementation = ArrayNode.class)
        private ArrayNode items;

        public JobsResponse(ArrayNode items) {
            this.items = items;
        }
    }
}