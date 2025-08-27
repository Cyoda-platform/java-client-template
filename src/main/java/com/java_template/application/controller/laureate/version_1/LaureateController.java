package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.Data;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Dull controller acting as a proxy to EntityService for Laureate entity only.
 * No business logic here — workflows implement business behavior.
 */
@RestController
@RequestMapping("/api/laureates/v1")
@Tag(name = "Laureate API", description = "Proxy endpoints for Laureate entity (version 1)")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a Laureate entity by its technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLaureateByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null || dataPayload.getData().isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Laureate not found");
            }

            ObjectNode node = (ObjectNode) dataPayload.getData();
            LaureateResponse response = objectMapper.treeToValue(node, LaureateResponse.class);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request in getLaureateByTechnicalId: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLaureateByTechnicalId", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getLaureateByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Laureates", description = "Retrieve all Laureate entities (no pagination). This is a proxy to EntityService.getItems")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listLaureates() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();

            List<LaureateResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    if (data != null && !data.isNull()) {
                        LaureateResponse r = objectMapper.treeToValue(data, LaureateResponse.class);
                        responses.add(r);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request in listLaureates: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in listLaureates", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in listLaureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create Laureate", description = "Add a single Laureate entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Created",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> addLaureate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate payload", required = true)
            @RequestBody LaureateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body must be provided");
            }
            Laureate entity = objectMapper.convertValue(request, Laureate.class);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    entity
            );
            UUID entityId = idFuture.get();
            return ResponseEntity.ok(new IdResponse(entityId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request in addLaureate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addLaureate", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in addLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Laureates", description = "Add multiple Laureate entities in batch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Created",
                    content = @Content(schema = @Schema(implementation = BulkIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> addLaureatesBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Batch laureate payload", required = true)
            @RequestBody LaureateBulkRequest request) {
        try {
            if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
                throw new IllegalArgumentException("Items must be provided");
            }
            List<Laureate> entities = new ArrayList<>();
            for (LaureateRequest r : request.getItems()) {
                Laureate e = objectMapper.convertValue(r, Laureate.class);
                entities.add(e);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<String> strIds = new ArrayList<>();
            if (ids != null) {
                for (UUID id : ids) strIds.add(id.toString());
            }
            return ResponseEntity.ok(new BulkIdsResponse(strIds));
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request in addLaureatesBatch: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addLaureatesBatch", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding laureates batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in addLaureatesBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Laureate", description = "Update a Laureate entity by technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate payload", required = true)
            @RequestBody LaureateRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body must be provided");
            }
            Laureate entity = objectMapper.convertValue(request, Laureate.class);
            CompletableFuture<UUID> updatedId = entityService.updateItem(UUID.fromString(technicalId), entity);
            UUID id = updatedId.get();
            return ResponseEntity.ok(new IdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request in updateLaureate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateLaureate", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in updateLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Laureate", description = "Delete a Laureate entity by technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deleted",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            CompletableFuture<UUID> deletedId = entityService.deleteItem(UUID.fromString(technicalId));
            UUID id = deletedId.get();
            return ResponseEntity.ok(new IdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request in deleteLaureate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteLaureate", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in deleteLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Laureates by condition", description = "Retrieve Laureate entities matching provided condition (in-memory filtering)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchLaureates(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true)
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition must be provided");
            }
            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    condition,
                    true
            );
            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            List<LaureateResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    if (data != null && !data.isNull()) {
                        LaureateResponse r = objectMapper.treeToValue(data, LaureateResponse.class);
                        responses.add(r);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request in searchLaureates: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchLaureates", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in searchLaureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs for request/response payloads (as per functional requirements)
    @Data
    @Schema(name = "LaureateResponse", description = "Laureate response payload")
    public static class LaureateResponse {
        @Schema(description = "Source id from API", example = "853")
        private Integer id;

        @Schema(description = "First name", example = "Akira")
        private String firstname;

        @Schema(description = "Surname", example = "Suzuki")
        private String surname;

        @Schema(description = "Year of award", example = "2010")
        private String year;

        @Schema(description = "Category of award", example = "Chemistry")
        private String category;

        @Schema(description = "Validation status", example = "VALID")
        private String validationStatus;

        @Schema(description = "Age at award", example = "80")
        private Integer ageAtAward;

        @Schema(description = "Affiliation name", example = "University X")
        private String affiliationName;

        @Schema(description = "Affiliation city", example = "Tokyo")
        private String affiliationCity;

        @Schema(description = "Affiliation country", example = "Japan")
        private String affiliationCountry;

        @Schema(description = "Born date", example = "1939-09-12")
        private String born;

        @Schema(description = "Died date", example = "2015-01-01")
        private String died;

        @Schema(description = "Born country", example = "Japan")
        private String bornCountry;

        @Schema(description = "Born city", example = "Osaka")
        private String bornCity;

        @Schema(description = "Born country code", example = "JP")
        private String bornCountryCode;

        @Schema(description = "Gender", example = "male")
        private String gender;

        @Schema(description = "Last seen at timestamp", example = "2024-01-01T12:00:00Z")
        private String lastSeenAt;

        @Schema(description = "Motivation", example = "For contributions to ...")
        private String motivation;

        @Schema(description = "Normalized country code", example = "JP")
        private String normalizedCountryCode;
    }

    @Data
    @Schema(name = "LaureateRequest", description = "Laureate request payload")
    public static class LaureateRequest {
        @Schema(description = "Source id from API", example = "853")
        private Integer id;

        @Schema(description = "First name", example = "Akira")
        private String firstname;

        @Schema(description = "Surname", example = "Suzuki")
        private String surname;

        @Schema(description = "Year of award", example = "2010")
        private String year;

        @Schema(description = "Category of award", example = "Chemistry")
        private String category;

        @Schema(description = "Validation status", example = "PENDING")
        private String validationStatus;

        @Schema(description = "Age at award", example = "80")
        private Integer ageAtAward;

        @Schema(description = "Affiliation name", example = "University X")
        private String affiliationName;

        @Schema(description = "Affiliation city", example = "Tokyo")
        private String affiliationCity;

        @Schema(description = "Affiliation country", example = "Japan")
        private String affiliationCountry;

        @Schema(description = "Born date", example = "1939-09-12")
        private String born;

        @Schema(description = "Died date", example = "2015-01-01")
        private String died;

        @Schema(description = "Born country", example = "Japan")
        private String bornCountry;

        @Schema(description = "Born city", example = "Osaka")
        private String bornCity;

        @Schema(description = "Born country code", example = "JP")
        private String bornCountryCode;

        @Schema(description = "Gender", example = "male")
        private String gender;

        @Schema(description = "Last seen at timestamp", example = "2024-01-01T12:00:00Z")
        private String lastSeenAt;

        @Schema(description = "Motivation", example = "For contributions to ...")
        private String motivation;

        @Schema(description = "Normalized country code", example = "JP")
        private String normalizedCountryCode;
    }

    @Data
    @Schema(name = "LaureateBulkRequest", description = "Bulk laureate request payload")
    public static class LaureateBulkRequest {
        @Schema(description = "List of laureates to add")
        private List<LaureateRequest> items;
    }

    @Data
    @Schema(name = "IdResponse", description = "Simple ID response payload")
    public static class IdResponse {
        @Schema(description = "Technical id (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public IdResponse() {}

        public IdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "BulkIdsResponse", description = "Bulk create IDs response payload")
    public static class BulkIdsResponse {
        @Schema(description = "List of created technical ids")
        private List<String> technicalIds;

        public BulkIdsResponse() {}

        public BulkIdsResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }
}