package com.java_template.application.controller.salesrecord.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/sales")
@Tag(name = "SalesRecord Controller", description = "Proxy controller for SalesRecord entity (version 1). All business logic is handled by workflows.")
public class SalesRecordController {

    private static final Logger logger = LoggerFactory.getLogger(SalesRecordController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SalesRecordController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Get SalesRecord by technicalId", description = "Retrieve a single SalesRecord by its technical UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SalesRecordResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("SalesRecord not found");
            }
            SalesRecordResponse response = objectMapper.treeToValue(node, SalesRecordResponse.class);
            // attach technicalId if present in meta
            if (dataPayload.getMeta() != null && dataPayload.getMeta().has("entityId")) {
                response.setTechnicalId(dataPayload.getMeta().get("entityId").asText());
            } else {
                response.setTechnicalId(technicalId);
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in getById", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in getById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "List SalesRecords", description = "Retrieve all SalesRecords or filter by productId query parameter.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SalesRecordResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> list(
            @Parameter(name = "productId", description = "Optional filter by productId")
            @RequestParam(value = "productId", required = false) String productId) {
        try {
            List<DataPayload> dataPayloads;
            if (productId != null && !productId.isBlank()) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.productId", "EQUALS", productId)
                );
                CompletableFuture<List<DataPayload>> filteredFuture = entityService.getItemsByCondition(
                        SalesRecord.ENTITY_NAME,
                        SalesRecord.ENTITY_VERSION,
                        condition,
                        true
                );
                dataPayloads = filteredFuture.get();
            } else {
                CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                        SalesRecord.ENTITY_NAME,
                        SalesRecord.ENTITY_VERSION,
                        null, null, null
                );
                dataPayloads = itemsFuture.get();
            }

            List<SalesRecordResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    SalesRecordResponse resp = objectMapper.treeToValue(data, SalesRecordResponse.class);
                    if (payload.getMeta() != null && payload.getMeta().has("entityId")) {
                        resp.setTechnicalId(payload.getMeta().get("entityId").asText());
                    }
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in list", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in list", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in list", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Add a SalesRecord", description = "Add a single SalesRecord. Note: business workflows typically create SalesRecords; this endpoint proxies to entity service to persist.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> add(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "SalesRecord payload", required = true,
                    content = @Content(schema = @Schema(implementation = SalesRecordRequest.class)))
            @RequestBody SalesRecordRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            SalesRecord entity = new SalesRecord();
            entity.setRecordId(request.getRecordId());
            entity.setDateSold(request.getDateSold());
            entity.setProductId(request.getProductId());
            entity.setQuantity(request.getQuantity());
            entity.setRevenue(request.getRevenue());
            entity.setRawSource(request.getRawSource());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    SalesRecord.ENTITY_NAME,
                    SalesRecord.ENTITY_VERSION,
                    entity
            );
            UUID entityId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in add", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in add", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in add", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Add multiple SalesRecords", description = "Add multiple SalesRecords in bulk.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> addBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of SalesRecord payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SalesRecordRequest.class))))
            @RequestBody List<SalesRecordRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request list is required");
            List<SalesRecord> entities = new ArrayList<>();
            for (SalesRecordRequest req : requests) {
                SalesRecord entity = new SalesRecord();
                entity.setRecordId(req.getRecordId());
                entity.setDateSold(req.getDateSold());
                entity.setProductId(req.getProductId());
                entity.setQuantity(req.getQuantity());
                entity.setRevenue(req.getRevenue());
                entity.setRawSource(req.getRawSource());
                entities.add(entity);
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    SalesRecord.ENTITY_NAME,
                    SalesRecord.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<TechnicalIdResponse> resp = new ArrayList<>();
            if (ids != null) {
                for (UUID id : ids) {
                    TechnicalIdResponse r = new TechnicalIdResponse();
                    r.setTechnicalId(id.toString());
                    resp.add(r);
                }
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in addBulk", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addBulk", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in addBulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update SalesRecord", description = "Update a SalesRecord by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> update(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "SalesRecord payload", required = true,
                    content = @Content(schema = @Schema(implementation = SalesRecordRequest.class)))
            @RequestBody SalesRecordRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            SalesRecord entity = new SalesRecord();
            entity.setRecordId(request.getRecordId());
            entity.setDateSold(request.getDateSold());
            entity.setProductId(request.getProductId());
            entity.setQuantity(request.getQuantity());
            entity.setRevenue(request.getRevenue());
            entity.setRawSource(request.getRawSource());

            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    UUID.fromString(technicalId),
                    entity
            );
            UUID id = updatedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in update", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in update", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in update", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete SalesRecord", description = "Delete a SalesRecord by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> delete(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            CompletableFuture<UUID> deletedId = entityService.deleteItem(UUID.fromString(technicalId));
            UUID id = deletedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument in delete", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in delete", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in delete", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Static DTO classes

    @Data
    @Schema(description = "Request payload for creating/updating a SalesRecord")
    public static class SalesRecordRequest {
        @Schema(description = "Business record id", example = "s-20250825-01")
        private String recordId;

        @Schema(description = "ISO-8601 timestamp when sold", example = "2025-08-25T08:12:00Z")
        private String dateSold;

        @Schema(description = "Reference to productId", example = "p-123")
        private String productId;

        @Schema(description = "Quantity sold", example = "3")
        private Integer quantity;

        @Schema(description = "Revenue for this record", example = "38.97")
        private Double revenue;

        @Schema(description = "Raw payload source", example = "{\"foo\":\"bar\"}")
        private String rawSource;
    }

    @Data
    @Schema(description = "Response payload for SalesRecord")
    public static class SalesRecordResponse {
        @Schema(description = "Technical entity id (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Business record id", example = "s-20250825-01")
        private String recordId;

        @Schema(description = "ISO-8601 timestamp when sold", example = "2025-08-25T08:12:00Z")
        private String dateSold;

        @Schema(description = "Reference to productId", example = "p-123")
        private String productId;

        @Schema(description = "Quantity sold", example = "3")
        private Integer quantity;

        @Schema(description = "Revenue for this record", example = "38.97")
        private Double revenue;

        @Schema(description = "Raw payload source", example = "{\"foo\":\"bar\"}")
        private String rawSource;
    }

    @Data
    @Schema(description = "Response containing only technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical entity id (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }
}