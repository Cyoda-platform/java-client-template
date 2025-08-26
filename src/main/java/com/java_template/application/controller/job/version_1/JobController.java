package com.java_template.application.controller.job.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "JobController", description = "Controller for Job entity (version 1)")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Job", description = "Create a new Job entity. Returns the technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = JobRequestDto.class)))
            @RequestBody JobRequestDto request
    ) {
        try {
            Job job = new Job();
            job.setJobId(request.getJobId());
            job.setName(request.getName());
            job.setSourceEndpoint(request.getSourceEndpoint());
            job.setSchedule(request.getSchedule());
            job.setParameters(request.getParameters());
            // Other optional fields may be set by workflows; controller remains a proxy.

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for creating Job", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating Job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when creating Job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Jobs", description = "Create multiple Job entities in batch. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createJobsBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Batch job creation payload", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobRequestDto.class))))
            @RequestBody List<JobRequestDto> requests
    ) {
        try {
            List<Job> jobs = requests.stream().map(r -> {
                Job j = new Job();
                j.setJobId(r.getJobId());
                j.setName(r.getName());
                j.setSourceEndpoint(r.getSourceEndpoint());
                j.setSchedule(r.getSchedule());
                j.setParameters(r.getParameters());
                return j;
            }).collect(Collectors.toList());

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    jobs
            );

            List<UUID> ids = idsFuture.get();
            List<String> stringIds = ids.stream().map(UUID::toString).collect(Collectors.toList());
            TechnicalIdsResponse resp = new TechnicalIdsResponse(stringIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid batch request for creating Jobs", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating Jobs batch", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when creating Jobs batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Job", description = "Retrieve a Job by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            JobResponseDto resp = objectMapper.convertValue(node, JobResponseDto.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId for getJob", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when retrieving Job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving Job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List all Jobs", description = "Retrieve all Job entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobResponseDto.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<JobResponseDto> respList = objectMapper.convertValue(array, objectMapper.getTypeFactory().constructCollectionType(List.class, JobResponseDto.class));
            return ResponseEntity.ok(respList);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when listing Jobs", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when listing Jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Jobs by condition", description = "Search Jobs using SearchConditionRequest. Returns matching jobs.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobResponseDto.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest searchRequest
    ) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    searchRequest,
                    true
            );
            ArrayNode array = filteredItemsFuture.get();
            List<JobResponseDto> respList = objectMapper.convertValue(array, objectMapper.getTypeFactory().constructCollectionType(List.class, JobResponseDto.class));
            return ResponseEntity.ok(respList);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid search request for Jobs", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when searching Jobs", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when searching Jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Job", description = "Update an existing Job by technicalId. Returns the technicalId of updated entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job update payload", required = true,
                    content = @Content(schema = @Schema(implementation = JobRequestDto.class)))
            @RequestBody JobRequestDto request
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            Job job = new Job();
            job.setJobId(request.getJobId());
            job.setName(request.getName());
            job.setSourceEndpoint(request.getSourceEndpoint());
            job.setSchedule(request.getSchedule());
            job.setParameters(request.getParameters());
            // controller remains a proxy, no business logic

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    id,
                    job
            );

            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for updating Job", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when updating Job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when updating Job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Job", description = "Delete a Job by technicalId. Returns the technicalId of deleted entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId for deleteJob", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when deleting Job", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when deleting Job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "JobRequestDto", description = "Job creation/update request")
    public static class JobRequestDto {
        @Schema(description = "Business job identifier", example = "job_2025_01")
        private String jobId;

        @Schema(description = "Human friendly name", example = "Daily Nobel Poll")
        private String name;

        @Schema(description = "Source endpoint (URL)", example = "https://api.opendatasoft/…")
        private String sourceEndpoint;

        @Schema(description = "Schedule (cron)", example = "0 0 * * *")
        private String schedule;

        @Schema(description = "Arbitrary parameters map")
        private java.util.Map<String, Object> parameters;
    }

    @Data
    @Schema(name = "JobResponseDto", description = "Job response payload")
    public static class JobResponseDto {
        @Schema(description = "Business job identifier", example = "job_2025_01")
        private String jobId;

        @Schema(description = "Human friendly name", example = "Daily Nobel Poll")
        private String name;

        @Schema(description = "Source endpoint (URL)")
        private String sourceEndpoint;

        @Schema(description = "Schedule (cron)")
        private String schedule;

        @Schema(description = "Creation timestamp")
        private String createdAt;

        @Schema(description = "Current job status", example = "COMPLETED")
        private String status;

        @Schema(description = "Timestamp of last run")
        private String lastRunAt;

        @Schema(description = "Number of automatic retry attempts made")
        private Integer retryCount;

        @Schema(description = "Job-specific parameters map")
        private java.util.Map<String, Object> parameters;

        @Schema(description = "Short summary of last run")
        private String resultSummary;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing a technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the entity")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing multiple technical ids")
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical ids")
        private List<String> technicalIds;

        public TechnicalIdsResponse() {}

        public TechnicalIdsResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }
}