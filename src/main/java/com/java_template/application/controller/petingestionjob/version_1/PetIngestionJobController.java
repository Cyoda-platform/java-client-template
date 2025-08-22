package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/jobs/ingestPets")
@Tag(name = "PetIngestionJob Controller", description = "Controller proxy for PetIngestionJob entity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Pet Ingestion Job", description = "Creates a new PetIngestionJob and returns its technicalId. Starts ingestion workflow asynchronously.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created",
                    content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<CreateResponse> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet ingestion job creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreatePetIngestionJobRequest.class)))
            @RequestBody CreatePetIngestionJobRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            ObjectNode data = objectMapper.valueToTree(request);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    PetIngestionJob.ENTITY_NAME,
                    String.valueOf(PetIngestionJob.ENTITY_VERSION),
                    data
            );
            UUID technicalId = idFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(String.format("/jobs/ingestPets/%s", technicalId.toString())));
            return new ResponseEntity<>(resp, headers, HttpStatus.CREATED);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createJob", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in createJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Pet Ingestion Job", description = "Retrieves a PetIngestionJob by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = PetIngestionJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<JsonNode> getJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    PetIngestionJob.ENTITY_NAME,
                    String.valueOf(PetIngestionJob.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getJob", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in getJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "List all Pet Ingestion Jobs", description = "Retrieves all PetIngestionJob entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetIngestionJobResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<ArrayNode> listJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    PetIngestionJob.ENTITY_NAME,
                    String.valueOf(PetIngestionJob.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in listJobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing jobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in listJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Search Pet Ingestion Jobs", description = "Performs a simple field-based search on PetIngestionJob entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetIngestionJobResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/search")
    public ResponseEntity<ArrayNode> searchJobs(
            @Parameter(description = "Field name to filter (e.g. status)") @RequestParam String field,
            @Parameter(description = "Operator (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.)") @RequestParam String operator,
            @Parameter(description = "Value to compare") @RequestParam String value
    ) {
        try {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("field is required");
            }
            Condition cond = Condition.of(String.format("$.%s", field), operator, value);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", cond);
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    PetIngestionJob.ENTITY_NAME,
                    String.valueOf(PetIngestionJob.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = filteredItemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search parameters", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in searchJobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching jobs", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in searchJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Update Pet Ingestion Job", description = "Updates an existing PetIngestionJob by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<CreateResponse> updateJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pet ingestion job update payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreatePetIngestionJobRequest.class)))
            @RequestBody CreatePetIngestionJobRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            UUID id = UUID.fromString(technicalId);
            ObjectNode data = objectMapper.valueToTree(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    PetIngestionJob.ENTITY_NAME,
                    String.valueOf(PetIngestionJob.ENTITY_VERSION),
                    id,
                    data
            );
            UUID updatedId = updatedIdFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateJob", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in updateJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in updateJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete Pet Ingestion Job", description = "Deletes a PetIngestionJob by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<CreateResponse> deleteJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    PetIngestionJob.ENTITY_NAME,
                    String.valueOf(PetIngestionJob.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedIdFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for deleteJob", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in deleteJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting job", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in deleteJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    @Schema(name = "CreatePetIngestionJobRequest", description = "Request payload to create or update a PetIngestionJob")
    public static class CreatePetIngestionJobRequest {
        @Schema(description = "Source descriptor or URL for the pet store", example = "https://petstore.example/api/pets", required = true)
        private String source;

        @Schema(description = "User or system that requested the ingestion", example = "user_123", required = true)
        private String requestedBy;

        @Schema(description = "Optional startedAt timestamp (ISO 8601)", example = "2023-01-01T12:00:00Z")
        private String startedAt;

        @Schema(description = "Optional completedAt timestamp (ISO 8601)", example = "2023-01-01T12:05:00Z")
        private String completedAt;

        @Schema(description = "Status of the job (pending|running|completed|failed)", example = "pending")
        private String status;

        @Schema(description = "Number of imported records", example = "0")
        private Integer importedCount;

        @Schema(description = "Errors encountered during ingestion")
        private String[] errors;
    }

    @Data
    @Schema(name = "CreateResponse", description = "Response returned after resource creation")
    public static class CreateResponse {
        @Schema(description = "Technical ID of the created/modified entity", example = "job_abc123")
        private String technicalId;
    }

    @Data
    @Schema(name = "PetIngestionJobResponse", description = "Representation of PetIngestionJob")
    public static class PetIngestionJobResponse {
        @Schema(description = "Business or technical job id", example = "job_abc123")
        private String jobId;

        @Schema(description = "Source descriptor or URL", example = "https://petstore.example/api/pets")
        private String source;

        @Schema(description = "Who requested the job", example = "user_123")
        private String requestedBy;

        @Schema(description = "When the job started (ISO 8601)")
        private String startedAt;

        @Schema(description = "When the job completed (ISO 8601)")
        private String completedAt;

        @Schema(description = "Job status (pending|running|completed|failed)", example = "completed")
        private String status;

        @Schema(description = "Number of imported records", example = "24")
        private Integer importedCount;

        @Schema(description = "List of errors encountered")
        private String[] errors;

        @Schema(description = "Entity creation timestamp")
        private String createdAt;

        @Schema(description = "Entity last update timestamp")
        private String updatedAt;
    }
}