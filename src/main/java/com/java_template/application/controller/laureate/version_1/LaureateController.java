package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.common.DataPayload;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Dull proxy controller for Laureate entity. All business logic is implemented in workflows.
 */
@RestController
@RequestMapping("/api/v1/laureate")
@Tag(name = "Laureate", description = "Proxy API for Laureate entity (version 1)")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public LaureateController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Laureate", description = "Persist a single Laureate entity. Controller is a proxy to EntityService.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createLaureate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate payload", required = true,
                    content = @Content(schema = @Schema(implementation = LaureateRequest.class)))
            @RequestBody Laureate request
    ) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    request
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create Laureate: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating Laureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating Laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when creating Laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Laureates", description = "Persist multiple Laureate entities in bulk.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createLaureatesBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of Laureate payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateRequest.class))))
            @RequestBody List<Laureate> requests
    ) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request body must contain at least one Laureate");
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    requests
            );
            List<UUID> ids = idsFuture.get();
            List<TechnicalIdResponse> resp = new ArrayList<>();
            for (UUID id : ids) {
                TechnicalIdResponse t = new TechnicalIdResponse();
                t.setTechnicalId(id.toString());
                resp.add(t);
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid batch create request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating Laureates batch", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating Laureates batch", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when creating Laureates batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a single Laureate by technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLaureateById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get Laureate: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when retrieving Laureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when retrieving Laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving Laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Laureates", description = "Retrieve all Laureate entities.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllLaureates() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            ArrayNode nodes = objectMapper.createArrayNode();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    nodes.add(payload.getData());
                }
            }
            return ResponseEntity.ok(nodes);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException when retrieving all Laureates", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when retrieving all Laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving all Laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Laureates by simple condition", description = "Search Laureate entities by a simple field-based condition. Provide fieldName, operator and value as query parameters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/search")
    public ResponseEntity<?> searchLaureates(
            @RequestParam(name = "fieldName") String fieldName,
            @RequestParam(name = "operator", required = false, defaultValue = "EQUALS") String operator,
            @RequestParam(name = "value") String value
    ) {
        try {
            if (fieldName == null || fieldName.isBlank()) throw new IllegalArgumentException("fieldName is required");
            if (value == null) throw new IllegalArgumentException("value is required");

            String jsonPath = "$." + fieldName;
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of(jsonPath, operator, value)
            );

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    condition,
                    true
            );
            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            ArrayNode results = objectMapper.createArrayNode();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    results.add(payload.getData());
                }
            }
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request for Laureates: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when searching Laureates", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when searching Laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when searching Laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Laureate by technicalId", description = "Update a Laureate entity by technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate payload", required = true,
                    content = @Content(schema = @Schema(implementation = LaureateRequest.class)))
            @RequestBody Laureate request
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");
            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    UUID.fromString(technicalId),
                    request
            );
            UUID id = updatedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid update request for Laureate: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when updating Laureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when updating Laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when updating Laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Laureate by technicalId", description = "Delete a Laureate entity by technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<UUID> deletedId = entityService.deleteItem(UUID.fromString(technicalId));
            UUID id = deletedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid delete request for Laureate: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when deleting Laureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when deleting Laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when deleting Laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs for requests/responses (Swagger annotated)

    @Data
    @Schema(name = "LaureateRequest", description = "Laureate request payload (maps to Laureate entity fields)")
    public static class LaureateRequest {
        private String affiliationCity;
        private String affiliationCountry;
        private String affiliationName;
        private Integer ageAtAward;
        private String awardYear;
        private String born;
        private String borncity;
        private String borncountry;
        private String borncountrycode;
        private String category;
        private String died;
        private String firstname;
        private String gender;
        private String laureateId;
        private String motivation;
        private String processingStatus;
        private ProvenanceDto provenance;
        private String surname;
        private List<String> validationErrors;
    }

    @Data
    @Schema(name = "LaureateResponse", description = "Laureate response payload (subset shown in functional requirements)")
    public static class LaureateResponse {
        private String laureateId;
        private String firstname;
        private String surname;
        private String processingStatus;
        private Integer ageAtAward;
        private ProvenanceDto provenance;
    }

    @Data
    @Schema(name = "ProvenanceDto", description = "Provenance information for a Laureate")
    public static class ProvenanceDto {
        private String ingestionJobId;
        private String sourceRecordId;
        private String sourceTimestamp;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technicalId")
    public static class TechnicalIdResponse {
        private String technicalId;
    }
}