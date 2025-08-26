package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
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
 * Dull proxy controller for Job entity.
 * All business logic is implemented in workflows; this controller only proxies requests to EntityService.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Job Controller", description = "Proxy endpoints for Job entity (version 1)")
public class JobController {

  private static final Logger logger = LoggerFactory.getLogger(JobController.class);

  private final EntityService entityService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public JobController(EntityService entityService) {
    this.entityService = entityService;
  }

  @Operation(summary = "Create Job", description = "Create a new Job and trigger the Job workflow. Returns technicalId.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "OK",
          content = @Content(schema = @Schema(implementation = JobCreateResponse.class))),
      @ApiResponse(responseCode = "400", description = "Bad Request"),
      @ApiResponse(responseCode = "500", description = "Internal Server Error")
  })
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "Job create request",
      required = true,
      content = @Content(schema = @Schema(implementation = JobCreateRequest.class))
  )
  @PostMapping
  public ResponseEntity<?> createJob(@Valid @RequestBody JobCreateRequest request) {
    try {
      ObjectNode payload = objectMapper.valueToTree(request);
      CompletableFuture<UUID> idFuture = entityService.addItem(
          com.java_template.application.entity.job.version_1.Job.ENTITY_NAME,
          String.valueOf(com.java_template.application.entity.job.version_1.Job.ENTITY_VERSION),
          payload
      );
      UUID technicalId = idFuture.get();
      JobCreateResponse resp = new JobCreateResponse();
      resp.setTechnicalId(technicalId.toString());
      return ResponseEntity.ok(resp);
    } catch (IllegalArgumentException e) {
      logger.warn("Invalid request for creating job", e);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof NoSuchElementException) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
      } else if (cause instanceof IllegalArgumentException) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
      } else {
        logger.error("Execution error while creating job", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
      }
    } catch (Exception e) {
      logger.error("Unexpected error while creating job", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
  }

  @Operation(summary = "Get Job by technicalId", description = "Retrieve a Job by its technicalId.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "OK",
          content = @Content(schema = @Schema(implementation = JobResponse.class))),
      @ApiResponse(responseCode = "400", description = "Bad Request"),
      @ApiResponse(responseCode = "404", description = "Not Found"),
      @ApiResponse(responseCode = "500", description = "Internal Server Error")
  })
  @GetMapping("/{technicalId}")
  public ResponseEntity<?> getJobById(
      @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
      @PathVariable("technicalId") String technicalId) {
    try {
      UUID tid = UUID.fromString(technicalId);
      CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
          com.java_template.application.entity.job.version_1.Job.ENTITY_NAME,
          String.valueOf(com.java_template.application.entity.job.version_1.Job.ENTITY_VERSION),
          tid
      );
      ObjectNode node = itemFuture.get();
      if (node == null || node.isNull()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
      }
      JobResponse resp = objectMapper.treeToValue(node, JobResponse.class);
      return ResponseEntity.ok(resp);
    } catch (IllegalArgumentException e) {
      logger.warn("Invalid technicalId for getJobById: {}", technicalId, e);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof NoSuchElementException) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
      } else if (cause instanceof IllegalArgumentException) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
      } else {
        logger.error("Execution error while retrieving job", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
      }
    } catch (Exception e) {
      logger.error("Unexpected error while retrieving job", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
  }

  @Operation(summary = "List Jobs", description = "Retrieve all Jobs.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "OK",
          content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = JobResponse.class)))),
      @ApiResponse(responseCode = "500", description = "Internal Server Error")
  })
  @GetMapping
  public ResponseEntity<?> listJobs() {
    try {
      CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
          com.java_template.application.entity.job.version_1.Job.ENTITY_NAME,
          String.valueOf(com.java_template.application.entity.job.version_1.Job.ENTITY_VERSION)
      );
      ArrayNode arrayNode = itemsFuture.get();
      List<JobResponse> results = new ArrayList<>();
      if (arrayNode != null) {
        for (JsonNode n : arrayNode) {
          JobResponse resp = objectMapper.treeToValue(n, JobResponse.class);
          results.add(resp);
        }
      }
      return ResponseEntity.ok(results);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IllegalArgumentException) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
      } else {
        logger.error("Execution error while listing jobs", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
      }
    } catch (Exception e) {
      logger.error("Unexpected error while listing jobs", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
  }

  // Static DTOs for request/response payloads

  @Data
  @Schema(name = "JobCreateRequest", description = "Payload to create a Job")
  public static class JobCreateRequest {
    @Schema(description = "Human job identifier (optional)", example = "job-nobel-2025-01")
    private String id;

    @NotBlank
    @Schema(description = "Data source location for ingestion", example = "https://public.opendata.example/nobel-laureates.csv", required = true)
    private String sourceUri;

    @NotBlank
    @Schema(description = "Schedule type (one-off or periodic)", example = "one-off", required = true)
    private String scheduleType;

    @Schema(description = "Cron or interval expression for schedule", example = "")
    private String scheduleExpression;

    @Schema(description = "Flag to notify all active subscribers", example = "true")
    @NotNull
    private Boolean notifyAllActive;

    @Schema(description = "Who created the job", example = "analyst@example.com")
    @NotBlank
    private String createdBy;
  }

  @Data
  @Schema(name = "JobCreateResponse", description = "Response after creating a Job")
  public static class JobCreateResponse {
    @Schema(description = "Technical ID assigned to the Job", example = "job-0001-uuid")
    private String technicalId;
  }

  @Data
  @Schema(name = "JobResponse", description = "Job representation returned by GET endpoints")
  public static class JobResponse {
    @Schema(description = "Technical ID assigned to the Job", example = "job-0001-uuid")
    private String technicalId;

    @Schema(description = "Human job identifier (optional)", example = "job-nobel-2025-01")
    private String id;

    @Schema(description = "Data source location for ingestion", example = "https://public.opendata.example/nobel-laureates.csv")
    private String sourceUri;

    @Schema(description = "Schedule type (one-off or periodic)", example = "one-off")
    private String scheduleType;

    @Schema(description = "Cron or interval expression for schedule", example = "")
    private String scheduleExpression;

    @Schema(description = "Current workflow state", example = "NOTIFIED_SUBSCRIBERS")
    private String status;

    @Schema(description = "Job start timestamp", example = "2025-01-10T10:00:00Z")
    private String startedAt;

    @Schema(description = "Job finish timestamp", example = "2025-01-10T10:01:05Z")
    private String finishedAt;

    @Schema(description = "Number of records fetched", example = "1200")
    private Integer totalsFetched;

    @Schema(description = "Number of records validated", example = "1190")
    private Integer totalsValid;

    @Schema(description = "Number of records failed validation", example = "10")
    private Integer totalsInvalid;

    @Schema(description = "Short description of job-level errors", example = "")
    private String errorSummary;

    @Schema(description = "Flag to notify subscribers", example = "true")
    private Boolean notifyAllActive;

    @Schema(description = "Who created the job", example = "analyst@example.com")
    private String createdBy;
  }
}