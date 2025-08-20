package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
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

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/entity/Job")
@Tag(name = "Job")
public class JobController {
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Create a new orchestration Job. requestId is recommended for idempotency.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request")
    })
    @PostMapping
    public ResponseEntity<?> createJob(@RequestBody CreateJobRequest request) {
        try {
            if (request == null || request.getRequestId() == null || request.getRequestId().isBlank()) {
                throw new IllegalArgumentException("requestId is required for idempotency");
            }
            ObjectNode data = objectMapper.convertValue(request, ObjectNode.class);
            UUID id = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    data
            ).get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid create job request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (Exception e) {
            logger.error("Error creating job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Job", description = "Retrieve Job by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Job.class))),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getJob(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            ObjectNode node = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();
            return ResponseEntity.ok(node);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (Exception e) {
            logger.error("Error fetching job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Jobs", description = "List all Jobs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Job.class))))
    })
    @GetMapping
    public ResponseEntity<?> listJobs() {
        try {
            ArrayNode arr = entityService.getItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION)
            ).get();
            return ResponseEntity.ok(arr);
        } catch (Exception e) {
            logger.error("Error listing jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Job", description = "Update a Job by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class)))
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateJob(@PathVariable String technicalId, @RequestBody UpdateJobRequest request) {
        try {
            ObjectNode data = objectMapper.convertValue(request, ObjectNode.class);
            UUID updated = entityService.updateItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    data
            ).get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(updated.toString());
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (Exception e) {
            logger.error("Error updating job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Query Jobs", description = "Query Jobs by condition (in-memory)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Job.class))))
    })
    @PostMapping("/query")
    public ResponseEntity<?> queryJobs(@RequestBody SearchConditionRequest condition) {
        try {
            ArrayNode arr = entityService.getItemsByCondition(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    condition,
                    true
            ).get();
            return ResponseEntity.ok(arr);
        } catch (Exception e) {
            logger.error("Error querying jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class CreateJobRequest {
        @Schema(description = "Idempotency/request id", required = true)
        private String requestId;
        @Schema(description = "Job type")
        private String type;
        @Schema(description = "Target entity technical id")
        private String targetEntityId;
        @Schema(description = "Payload as JSON string")
        private String payload;
    }

    @Data
    static class UpdateJobRequest {
        @Schema(description = "Status of the job")
        private String status;
        @Schema(description = "Payload")
        private String payload;
    }

    @Data
    static class CreateResponse {
        @Schema(description = "Technical id of the created resource")
        private String technicalId;
    }
}
