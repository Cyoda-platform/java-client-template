package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.laureate.version_1.Laureate;

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

/**
 * Dull proxy controller for Laureate entity. All business logic is executed in workflows;
 * this controller only proxies requests to the EntityService.
 */
@RestController
@RequestMapping("/api/laureate/v1")
@Tag(name = "Laureate", description = "Laureate entity API (version 1) - proxy to EntityService")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a Laureate by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLaureateById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();

            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            LaureateResponse resp = objectMapper.treeToValue(node, LaureateResponse.class);
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument for getLaureateById: {}", technicalId, iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in getLaureateById", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getLaureateById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Laureates", description = "Retrieve all Laureate items")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllLaureates() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in getAllLaureates", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting all laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getAllLaureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Laureates by condition", description = "Search Laureates using a SearchConditionRequest (field-based conditions)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchLaureates(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition payload", content = @Content(schema = @Schema(implementation = SearchRequest.class)))
            @RequestBody SearchRequest request) {
        try {
            SearchConditionRequest condition = request.getCondition();
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in searchLaureates", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in searchLaureates", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in searchLaureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Laureate", description = "Update a Laureate by technicalId. Business rules are executed in workflows; controller only proxies update.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate update payload", content = @Content(schema = @Schema(implementation = UpdateLaureateRequest.class)))
            @RequestBody UpdateLaureateRequest updateRequest) {
        try {
            UUID id = UUID.fromString(technicalId);
            // Map DTO to entity
            Laureate entity = objectMapper.convertValue(updateRequest, Laureate.class);

            CompletableFuture<java.util.UUID> updatedIdFuture = entityService.updateItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    id,
                    entity
            );
            UUID updatedId = updatedIdFuture.get();
            UpdateResponse resp = new UpdateResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in updateLaureate: {}", technicalId, iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in updateLaureate", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in updateLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Laureate", description = "Delete a Laureate by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<java.util.UUID> deletedIdFuture = entityService.deleteItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedIdFuture.get();
            DeleteResponse resp = new DeleteResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid argument in deleteLaureate: {}", technicalId, iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in deleteLaureate", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in deleteLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ----------------------------
    // Static DTO classes for API payloads/responses
    // ----------------------------

    @Data
    @Schema(name = "LaureateResponse", description = "Laureate response payload")
    public static class LaureateResponse {
        @Schema(description = "Technical ID of the entity")
        private String technicalId;

        @Schema(description = "External ID from source")
        private String externalId;

        @Schema(description = "Display full name")
        private String fullName;

        @Schema(description = "Birth date (ISO)")
        private String birthDate;

        @Schema(description = "Country of affiliation")
        private String country;

        @Schema(description = "Prize year")
        private Integer prizeYear;

        @Schema(description = "Prize category")
        private String prizeCategory;

        @Schema(description = "Prize motivation/description")
        private String motivation;

        @Schema(description = "Raw payload from source")
        private String rawPayload;

        @Schema(description = "First seen timestamp (ISO)")
        private String firstSeenTimestamp;

        @Schema(description = "Last seen timestamp (ISO)")
        private String lastSeenTimestamp;

        @Schema(description = "Change summary")
        private String changeSummary;
    }

    @Data
    @Schema(name = "SearchRequest", description = "Wrapper for SearchConditionRequest used to search Laureates")
    public static class SearchRequest {
        @Schema(description = "Search condition request", implementation = SearchConditionRequest.class)
        private SearchConditionRequest condition;
    }

    @Data
    @Schema(name = "UpdateLaureateRequest", description = "Payload to update Laureate fields")
    public static class UpdateLaureateRequest {
        @Schema(description = "External ID from source")
        private String externalId;

        @Schema(description = "Display full name")
        private String fullName;

        @Schema(description = "Birth date (ISO)")
        private String birthDate;

        @Schema(description = "Country of affiliation")
        private String country;

        @Schema(description = "Prize year")
        private Integer prizeYear;

        @Schema(description = "Prize category")
        private String prizeCategory;

        @Schema(description = "Prize motivation/description")
        private String motivation;

        @Schema(description = "Raw payload from source")
        private String rawPayload;

        @Schema(description = "First seen timestamp (ISO)")
        private String firstSeenTimestamp;

        @Schema(description = "Last seen timestamp (ISO)")
        private String lastSeenTimestamp;

        @Schema(description = "Change summary")
        private String changeSummary;
    }

    @Data
    @Schema(name = "UpdateResponse", description = "Response containing the technicalId of the updated entity")
    public static class UpdateResponse {
        @Schema(description = "Technical ID of the entity")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteResponse", description = "Response containing the technicalId of the deleted entity")
    public static class DeleteResponse {
        @Schema(description = "Technical ID of the entity")
        private String technicalId;
    }
}