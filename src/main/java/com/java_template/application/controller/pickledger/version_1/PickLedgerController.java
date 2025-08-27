package com.java_template.application.controller.pickledger.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pickledger.version_1.PickLedger;
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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Tag(name = "PickLedger Controller", description = "CRUD endpoints for PickLedger entity (v1). Controller is a thin proxy to EntityService.")
@RestController
@RequestMapping("/entity/pickledger/v1")
public class PickLedgerController {

    private static final Logger logger = LoggerFactory.getLogger(PickLedgerController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EntityService entityService;

    public PickLedgerController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create PickLedger", description = "Create a PickLedger entity. Returns technicalId of created entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPickLedger(@org.springframework.web.bind.annotation.RequestBody CreatePickLedgerRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            PickLedger entity = toEntity(request);
            UUID id = entityService.addItem(
                    PickLedger.ENTITY_NAME,
                    String.valueOf(PickLedger.ENTITY_VERSION),
                    entity
            ).get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create PickLedger: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating PickLedger", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when creating PickLedger", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple PickLedger entries", description = "Create multiple PickLedger entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdsResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createPickLedgerBatch(@org.springframework.web.bind.annotation.RequestBody BatchCreateRequest request) {
        try {
            if (request == null || request.getItems() == null) throw new IllegalArgumentException("Request body with items is required");
            List<PickLedger> entities = new ArrayList<>();
            for (CreatePickLedgerRequest r : request.getItems()) {
                entities.add(toEntity(r));
            }
            List<java.util.UUID> ids = entityService.addItems(
                    PickLedger.ENTITY_NAME,
                    String.valueOf(PickLedger.ENTITY_VERSION),
                    entities
            ).get();
            List<String> strIds = new ArrayList<>();
            for (UUID u : ids) strIds.add(u.toString());
            return ResponseEntity.ok(new TechnicalIdsResponse(strIds));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid batch create request for PickLedger: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when batch creating PickLedger", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when batch creating PickLedger", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get PickLedger by technicalId", description = "Retrieve a PickLedger by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PickLedgerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPickLedger(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);
            ObjectNode node = entityService.getItem(
                    PickLedger.ENTITY_NAME,
                    String.valueOf(PickLedger.ENTITY_VERSION),
                    tid
            ).get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PickLedger not found");
            }
            PickLedgerResponse resp = MAPPER.treeToValue(node, PickLedgerResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getPickLedger: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when retrieving PickLedger", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving PickLedger", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all PickLedger items", description = "Retrieve all PickLedger entries.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PickLedgerResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllPickLedgers() {
        try {
            ArrayNode array = entityService.getItems(
                    PickLedger.ENTITY_NAME,
                    String.valueOf(PickLedger.ENTITY_VERSION)
            ).get();
            List<PickLedgerResponse> results = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    ObjectNode node = (ObjectNode) array.get(i);
                    results.add(MAPPER.treeToValue(node, PickLedgerResponse.class));
                }
            }
            return ResponseEntity.ok(results);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when retrieving all PickLedgers", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving all PickLedgers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search PickLedger items by basic condition", description = "Search PickLedger entries by a simple field/operator/value condition.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PickLedgerResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchPickLedgers(@org.springframework.web.bind.annotation.RequestBody SearchRequest request) {
        try {
            if (request == null || request.getFieldName() == null || request.getOperator() == null || request.getValue() == null) {
                throw new IllegalArgumentException("fieldName, operator and value are required");
            }
            SearchConditionRequest condition = SearchConditionRequest.group(
                    "AND",
                    Condition.of("$." + request.getFieldName(), request.getOperator(), request.getValue())
            );
            ArrayNode array = entityService.getItemsByCondition(
                    PickLedger.ENTITY_NAME,
                    String.valueOf(PickLedger.ENTITY_VERSION),
                    condition,
                    true
            ).get();
            List<PickLedgerResponse> results = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    ObjectNode node = (ObjectNode) array.get(i);
                    results.add(MAPPER.treeToValue(node, PickLedgerResponse.class));
                }
            }
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request for PickLedger: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when searching PickLedgers", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when searching PickLedgers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update PickLedger", description = "Update a PickLedger entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePickLedger(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @org.springframework.web.bind.annotation.RequestBody UpdatePickLedgerRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            UUID tid = UUID.fromString(technicalId);
            PickLedger entity = toEntity(request);
            UUID updated = entityService.updateItem(
                    PickLedger.ENTITY_NAME,
                    String.valueOf(PickLedger.ENTITY_VERSION),
                    tid,
                    entity
            ).get();
            return ResponseEntity.ok(new TechnicalIdResponse(updated.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid update request for PickLedger: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when updating PickLedger", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when updating PickLedger", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete PickLedger", description = "Delete a PickLedger by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePickLedger(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);
            UUID deleted = entityService.deleteItem(
                    PickLedger.ENTITY_NAME,
                    String.valueOf(PickLedger.ENTITY_VERSION),
                    tid
            ).get();
            return ResponseEntity.ok(new TechnicalIdResponse(deleted.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for deletePickLedger: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when deleting PickLedger", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when deleting PickLedger", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Helper to map request DTO to entity
    private PickLedger toEntity(CreatePickLedgerRequest req) {
        PickLedger e = new PickLedger();
        // Controller is thin proxy; simple mapping of fields
        e.setPickId(req.getPickId());
        e.setOrderId(req.getOrderId());
        e.setShipmentId(req.getShipmentId());
        e.setSku(req.getSku());
        e.setActor(req.getActor());
        e.setAt(req.getAt());
        e.setDelta(req.getDelta());
        e.setNote(req.getNote());
        return e;
    }

    private PickLedger toEntity(UpdatePickLedgerRequest req) {
        return toEntity((CreatePickLedgerRequest) req);
    }

    // DTOs

    @Data
    @Schema(name = "CreatePickLedgerRequest", description = "Payload to create a PickLedger")
    public static class CreatePickLedgerRequest {
        @Schema(description = "pickId (technical id, optional for create)", example = "00000000-0000-0000-0000-000000000000")
        private String pickId;
        @Schema(description = "orderId (optional)", example = "00000000-0000-0000-0000-000000000001")
        private String orderId;
        @Schema(description = "shipmentId (optional)", example = "00000000-0000-0000-0000-000000000002")
        private String shipmentId;
        @Schema(description = "sku", required = true, example = "SKU-12345")
        private String sku;
        @Schema(description = "actor (optional)", example = "picker-1")
        private String actor;
        @Schema(description = "at ISO-8601 timestamp", required = true, example = "2025-08-27T12:00:00Z")
        private String at;
        @Schema(description = "delta quantity changed (integer)", required = true, example = "1")
        private Integer delta;
        @Schema(description = "note (optional)", example = "Picked from shelf A3")
        private String note;
    }

    @Data
    @Schema(name = "UpdatePickLedgerRequest", description = "Payload to update a PickLedger")
    public static class UpdatePickLedgerRequest extends CreatePickLedgerRequest {
    }

    @Data
    @Schema(name = "PickLedgerResponse", description = "PickLedger entity response")
    public static class PickLedgerResponse {
        @Schema(description = "pickId (technical id)", example = "00000000-0000-0000-0000-000000000000")
        private String pickId;
        @Schema(description = "orderId (optional)", example = "00000000-0000-0000-0000-000000000001")
        private String orderId;
        @Schema(description = "shipmentId (optional)", example = "00000000-0000-0000-0000-000000000002")
        private String shipmentId;
        @Schema(description = "sku", example = "SKU-12345")
        private String sku;
        @Schema(description = "actor (optional)", example = "picker-1")
        private String actor;
        @Schema(description = "at ISO-8601 timestamp", example = "2025-08-27T12:00:00Z")
        private String at;
        @Schema(description = "delta quantity changed (integer)", example = "1")
        private Integer delta;
        @Schema(description = "note (optional)", example = "Picked from shelf A3")
        private String note;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing a single technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "technicalId", example = "00000000-0000-0000-0000-000000000000")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing list of technicalIds")
    public static class TechnicalIdsResponse {
        @Schema(description = "technicalIds")
        private List<String> technicalIds;

        public TechnicalIdsResponse() {}

        public TechnicalIdsResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "BatchCreateRequest", description = "Batch create request for multiple PickLedger items")
    public static class BatchCreateRequest {
        @Schema(description = "items to create")
        private List<CreatePickLedgerRequest> items;
    }

    @Data
    @Schema(name = "SearchRequest", description = "Simple search condition for PickLedger")
    public static class SearchRequest {
        @Schema(description = "field name to search (top-level field)", example = "sku", required = true)
        private String fieldName;
        @Schema(description = "operator (EQUALS, NOT_EQUAL, GREATER_THAN, LESS_THAN, IEQUALS, etc.)", example = "EQUALS", required = true)
        private String operator;
        @Schema(description = "value to compare (string)", example = "SKU-12345", required = true)
        private String value;
    }
}