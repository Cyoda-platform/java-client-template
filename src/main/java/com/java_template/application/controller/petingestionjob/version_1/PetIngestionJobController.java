package com.java_template.application.controller.petingestionjob.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob; // NOSONAR - placeholder import reference
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.UUID;

/**
 * Dull proxy controller for PetIngestionJob entity only.
 * Proxies requests to EntityService. No business logic here.
 */
@Tag(name = "PetIngestionJob")
@RestController
@RequestMapping("/jobs/ingestPets")
@RequiredArgsConstructor
public class PetIngestionJobController {

    private static final Logger logger = LoggerFactory.getLogger(PetIngestionJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "Create Pet Ingestion Job", description = "Creates a PetIngestionJob. Returns technicalId and Location header. Controller proxies to EntityService.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Job creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateRequestDto.class)))
            @RequestBody CreateRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            ObjectNode payload = objectMapper.createObjectNode();
            if (request.getSource() != null) payload.put("source", request.getSource());
            if (request.getRequestedBy() != null) payload.put("requestedBy", request.getRequestedBy());
            // Do not set workflow/status here. Controller must not implement business logic.

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    PetIngestionJob.ENTITY_NAME,
                    String.valueOf(PetIngestionJob.ENTITY_VERSION),
                    payload
            );

            UUID technicalId = idFuture.get();
            CreateResponseDto resp = new CreateResponseDto();
            resp.setTechnicalId(technicalId.toString());

            HttpHeaders headers = new HttpHeaders();
            headers.add("Location", "/jobs/ingestPets/" + technicalId.toString());

            return new ResponseEntity<>(resp, headers, HttpStatus.CREATED);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createJob request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Pet Ingestion Jobs", description = "Batch create PetIngestionJob entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BatchCreateResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createJobsBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Batch job creation payload", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateRequestDto.class))))
            @RequestBody List<CreateRequestDto> requests
    ) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request list is required and cannot be empty");

            ArrayNode array = objectMapper.createArrayNode();
            for (CreateRequestDto r : requests) {
                ObjectNode node = objectMapper.createObjectNode();
                if (r.getSource() != null) node.put("source", r.getSource());
                if (r.getRequestedBy() != null) node.put("requestedBy", r.getRequestedBy());
                array.add(node);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    PetIngestionJob.ENTITY_NAME,
                    String.valueOf(PetIngestionJob.ENTITY_VERSION),
                    array
            );

            List<UUID> ids = idsFuture.get();
            BatchCreateResponseDto resp = new BatchCreateResponseDto();
            resp.setTechnicalIds(ids.stream().map(UUID::toString).collect(Collectors.toList()));
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createJobsBatch request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createJobsBatch", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createJobsBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Pet Ingestion Job by technicalId", description = "Retrieves the authoritative representation of a PetIngestionJob.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ItemResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    PetIngestionJob.ENTITY_NAME,
                    String.valueOf(PetIngestionJob.ENTITY_VERSION),
                    id
            );
            ObjectNode item = itemFuture.get();
            ItemResponseDto resp = new ItemResponseDto();
            resp.setData(item);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid getJob request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Pet Ingestion Jobs", description = "Retrieves all PetIngestionJob entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemListResponseDto.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    PetIngestionJob.ENTITY_NAME,
                    String.valueOf(PetIngestionJob.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            ItemListResponseDto resp = new ItemListResponseDto();
            resp.setItems(items);
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllJobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getAllJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Pet Ingestion Jobs by simple conditions", description = "Performs simple field based search using provided conditions (AND group).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemListResponseDto.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search conditions", required = true,
                    content = @Content(schema = @Schema(implementation = SearchRequestDto.class)))
            @RequestBody SearchRequestDto request
    ) {
        try {
            if (request == null || request.getConditions() == null || request.getConditions().isEmpty()) {
                throw new IllegalArgumentException("Search conditions are required");
            }

            List<Condition> conds = new ArrayList<>();
            for (SearchConditionDto c : request.getConditions()) {
                if (c.getField() == null || c.getOperator() == null) {
                    throw new IllegalArgumentException("Each condition requires field and operator");
                }
                String jsonPath = "$." + c.getField();
                conds.add(Condition.of(jsonPath, c.getOperator(), c.getValue()));
            }

            SearchConditionRequest condition = SearchConditionRequest.group("AND", conds.toArray(new Condition[0]));

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    PetIngestionJob.ENTITY_NAME,
                    String.valueOf(PetIngestionJob.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode items = filteredItemsFuture.get();
            ItemListResponseDto resp = new ItemListResponseDto();
            resp.setItems(items);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid searchJobs request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchJobs", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in searchJobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Pet Ingestion Job", description = "Updates a PetIngestionJob by technicalId. Proxies to EntityService.updateItem.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Update payload", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateRequestDto.class)))
            @RequestBody UpdateRequestDto request
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            if (request == null) throw new IllegalArgumentException("Request body is required");

            ObjectNode payload = objectMapper.createObjectNode();
            if (request.getSource() != null) payload.put("source", request.getSource());
            if (request.getRequestedBy() != null) payload.put("requestedBy", request.getRequestedBy());
            if (request.getStartedAt() != null) payload.put("startedAt", request.getStartedAt());
            if (request.getCompletedAt() != null) payload.put("completedAt", request.getCompletedAt());
            if (request.getStatus() != null) payload.put("status", request.getStatus());
            if (request.getImportedCount() != null) payload.put("importedCount", request.getImportedCount());
            if (request.getErrors() != null) {
                ArrayNode arr = objectMapper.createArrayNode();
                for (String e : request.getErrors()) arr.add(e);
                payload.set("errors", arr);
            }

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    PetIngestionJob.ENTITY_NAME,
                    String.valueOf(PetIngestionJob.ENTITY_VERSION),
                    id,
                    payload
            );

            UUID updatedId = updatedIdFuture.get();
            UpdateResponseDto resp = new UpdateResponseDto();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid updateJob request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in updateJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Pet Ingestion Job", description = "Deletes a PetIngestionJob by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    PetIngestionJob.ENTITY_NAME,
                    String.valueOf(PetIngestionJob.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedIdFuture.get();
            DeleteResponseDto resp = new DeleteResponseDto();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid deleteJob request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in deleteJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // -----------------------
    // DTOs
    // -----------------------

    @Data
    @Schema(name = "CreateRequestDto", description = "Payload to create a PetIngestionJob")
    public static class CreateRequestDto {
        @Schema(description = "Source URL or descriptor", example = "https://petstore.example/api/pets")
        private String source;
        @Schema(description = "Requested by user or system id", example = "user_123")
        private String requestedBy;
    }

    @Data
    @Schema(name = "CreateResponseDto", description = "Response containing created technicalId")
    public static class CreateResponseDto {
        @Schema(description = "Technical ID of created entity", example = "job_abc123")
        private String technicalId;
    }

    @Data
    @Schema(name = "BatchCreateResponseDto", description = "Response for batch create containing technicalIds")
    public static class BatchCreateResponseDto {
        @Schema(description = "List of created technical IDs")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "ItemResponseDto", description = "Single item response wrapper")
    public static class ItemResponseDto {
        @Schema(description = "Entity representation as stored")
        private JsonNode data;
    }

    @Data
    @Schema(name = "ItemListResponseDto", description = "List of items response wrapper")
    public static class ItemListResponseDto {
        @Schema(description = "Array of entity representations")
        private ArrayNode items;
    }

    @Data
    @Schema(name = "SearchRequestDto", description = "Simple search request with conditions")
    public static class SearchRequestDto {
        @Schema(description = "List of simple conditions to AND together")
        private List<SearchConditionDto> conditions;
    }

    @Data
    @Schema(name = "SearchConditionDto", description = "Single search condition")
    public static class SearchConditionDto {
        @Schema(description = "Field name (dot notation supported), e.g. status")
        private String field;
        @Schema(description = "Operator (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.)", example = "EQUALS")
        private String operator;
        @Schema(description = "Value to compare against", example = "pending")
        private String value;
    }

    @Data
    @Schema(name = "UpdateRequestDto", description = "Payload to update a PetIngestionJob")
    public static class UpdateRequestDto {
        @Schema(description = "Source URL or descriptor")
        private String source;
        @Schema(description = "Requested by user or system id")
        private String requestedBy;
        @Schema(description = "ISO timestamp when job started")
        private String startedAt;
        @Schema(description = "ISO timestamp when job completed")
        private String completedAt;
        @Schema(description = "Job status (pending|running|completed|failed)")
        private String status;
        @Schema(description = "Number of imported records")
        private Integer importedCount;
        @Schema(description = "List of error messages")
        private List<String> errors;
    }

    @Data
    @Schema(name = "UpdateResponseDto", description = "Response after update")
    public static class UpdateResponseDto {
        @Schema(description = "Technical ID of updated entity")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteResponseDto", description = "Response after delete")
    public static class DeleteResponseDto {
        @Schema(description = "Technical ID of deleted entity")
        private String technicalId;
    }
}