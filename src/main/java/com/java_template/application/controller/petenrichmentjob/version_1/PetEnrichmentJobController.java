package com.java_template.application.controller.petenrichmentjob.version_1;

import static com.java_template.common.config.Config.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.petenrichmentjob.version_1.PetEnrichmentJob;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import lombok.Data;

@RestController
@RequestMapping("/api/petenrichmentjob/v1")
@Tag(name = "PetEnrichmentJob", description = "Operations for PetEnrichmentJob entity")
public class PetEnrichmentJobController {

    private static final Logger logger = LoggerFactory.getLogger(PetEnrichmentJobController.class);

    private final EntityService entityService;

    public PetEnrichmentJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create PetEnrichmentJob", description = "Create a new PetEnrichmentJob")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreatePetEnrichmentJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPetEnrichmentJob(
            @Valid @RequestBody CreatePetEnrichmentJobRequest request) {
        try {
            if (request.getPetSource() == null || request.getPetSource().trim().isEmpty()) {
                throw new IllegalArgumentException("petSource must be provided");
            }

            PetEnrichmentJob job = new PetEnrichmentJob();
            job.setPetSource(request.getPetSource());
            job.setRequestedBy(request.getRequestedBy());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    PetEnrichmentJob.ENTITY_NAME,
                    String.valueOf(PetEnrichmentJob.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();

            CreatePetEnrichmentJobResponse resp = new CreatePetEnrichmentJobResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Validation failed when creating PetEnrichmentJob: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating PetEnrichmentJob", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating PetEnrichmentJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while creating PetEnrichmentJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple PetEnrichmentJobs", description = "Create multiple PetEnrichmentJob entities")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateManyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createPetEnrichmentJobsBulk(
            @Valid @RequestBody List<CreatePetEnrichmentJobRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request list must not be empty");
            }

            List<PetEnrichmentJob> jobs = requests.stream().map(r -> {
                PetEnrichmentJob j = new PetEnrichmentJob();
                j.setPetSource(r.getPetSource());
                j.setRequestedBy(r.getRequestedBy());
                return j;
            }).toList();

            CompletableFuture<List<java.util.UUID>> idsFuture = entityService.addItems(
                    PetEnrichmentJob.ENTITY_NAME,
                    String.valueOf(PetEnrichmentJob.ENTITY_VERSION),
                    jobs
            );

            List<UUID> technicalIds = idsFuture.get();
            CreateManyResponse resp = new CreateManyResponse();
            resp.setTechnicalIds(technicalIds.stream().map(UUID::toString).toList());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Validation failed when creating multiple PetEnrichmentJobs: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating multiple PetEnrichmentJobs", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating multiple PetEnrichmentJobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while creating multiple PetEnrichmentJobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get PetEnrichmentJob by technicalId", description = "Retrieve a PetEnrichmentJob by its technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetPetEnrichmentJobResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPetEnrichmentJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    PetEnrichmentJob.ENTITY_NAME,
                    String.valueOf(PetEnrichmentJob.ENTITY_VERSION),
                    id
            );

            ObjectNode item = itemFuture.get();
            GetPetEnrichmentJobResponse resp = new GetPetEnrichmentJobResponse();
            resp.setItem(item);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid argument while fetching PetEnrichmentJob {}: {}", technicalId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching PetEnrichmentJob {}", technicalId, ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching PetEnrichmentJob {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while fetching PetEnrichmentJob {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all PetEnrichmentJobs", description = "Retrieve all PetEnrichmentJobs")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetManyResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getPetEnrichmentJobs() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    PetEnrichmentJob.ENTITY_NAME,
                    String.valueOf(PetEnrichmentJob.ENTITY_VERSION)
            );

            ArrayNode items = itemsFuture.get();
            GetManyResponse resp = new GetManyResponse();
            resp.setItems(items);
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching PetEnrichmentJobs", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching PetEnrichmentJobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while fetching PetEnrichmentJobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search PetEnrichmentJobs by condition", description = "Retrieve PetEnrichmentJobs that match the provided search condition")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetManyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchPetEnrichmentJobs(@RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("condition must be provided");
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    PetEnrichmentJob.ENTITY_NAME,
                    String.valueOf(PetEnrichmentJob.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode items = filteredItemsFuture.get();
            GetManyResponse resp = new GetManyResponse();
            resp.setItems(items);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid search condition: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching PetEnrichmentJobs", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching PetEnrichmentJobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while searching PetEnrichmentJobs", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update PetEnrichmentJob", description = "Update an existing PetEnrichmentJob by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePetEnrichmentJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
            @Valid @RequestBody CreatePetEnrichmentJobRequest request) {
        try {
            UUID id = UUID.fromString(technicalId);

            PetEnrichmentJob job = new PetEnrichmentJob();
            job.setPetSource(request.getPetSource());
            job.setRequestedBy(request.getRequestedBy());

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    PetEnrichmentJob.ENTITY_NAME,
                    String.valueOf(PetEnrichmentJob.ENTITY_VERSION),
                    id,
                    job
            );

            UUID updatedId = updatedIdFuture.get();
            UpdateResponse resp = new UpdateResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid argument while updating PetEnrichmentJob {}: {}", technicalId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating PetEnrichmentJob {}", technicalId, ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating PetEnrichmentJob {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while updating PetEnrichmentJob {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete PetEnrichmentJob", description = "Delete a PetEnrichmentJob by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePetEnrichmentJob(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    PetEnrichmentJob.ENTITY_NAME,
                    String.valueOf(PetEnrichmentJob.ENTITY_VERSION),
                    id
            );

            UUID deletedId = deletedIdFuture.get();
            DeleteResponse resp = new DeleteResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid argument while deleting PetEnrichmentJob {}: {}", technicalId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting PetEnrichmentJob {}", technicalId, ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting PetEnrichmentJob {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting PetEnrichmentJob {}", technicalId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Static DTOs

    @Data
    public static class CreatePetEnrichmentJobRequest {
        @Schema(description = "Pet source string", example = "petstore://pets?limit=10")
        private String petSource;

        @Schema(description = "Requested by user id or name", example = "user-123")
        private String requestedBy;
    }

    @Data
    public static class CreatePetEnrichmentJobResponse {
        @Schema(description = "Technical ID of created entity")
        private String technicalId;
    }

    @Data
    public static class CreateManyResponse {
        @Schema(description = "List of technical IDs created")
        private List<String> technicalIds;
    }

    @Data
    public static class GetPetEnrichmentJobResponse {
        @Schema(description = "PetEnrichmentJob entity as JSON")
        private ObjectNode item;
    }

    @Data
    public static class GetManyResponse {
        @Schema(description = "List of PetEnrichmentJob entities")
        private ArrayNode items;
    }

    @Data
    public static class UpdateResponse {
        @Schema(description = "Technical ID of updated entity")
        private String technicalId;
    }

    @Data
    public static class DeleteResponse {
        @Schema(description = "Technical ID of deleted entity")
        private String technicalId;
    }
}