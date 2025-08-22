package com.java_template.application.controller.adoptionorder.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionorder.version_1.AdoptionOrder;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/adoptions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "AdoptionOrder Controller", description = "Proxy controller for AdoptionOrder entity (version 1)")
public class AdoptionOrderController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionOrderController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdoptionOrderController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create AdoptionOrder", description = "Create a new AdoptionOrder entity. Returns assigned technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createAdoptionOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "AdoptionOrder payload", required = true,
                    content = @Content(schema = @Schema(implementation = AdoptionOrderRequest.class)))
            @RequestBody AdoptionOrderRequest request) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.valueToTree(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    AdoptionOrder.ENTITY_NAME,
                    String.valueOf(AdoptionOrder.ENTITY_VERSION),
                    node
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for createAdoptionOrder", e);
            return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Failed to create AdoptionOrder", e);
            return ResponseEntity.status(500).body(errorBody(e.getMessage()));
        }
    }

    @Operation(summary = "Create multiple AdoptionOrders", description = "Create multiple AdoptionOrder entities in batch. Returns assigned technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createAdoptionOrdersBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of AdoptionOrder payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdoptionOrderRequest.class))))
            @RequestBody List<AdoptionOrderRequest> requests) {
        try {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (AdoptionOrderRequest r : requests) {
                arrayNode.add(objectMapper.valueToTree(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    AdoptionOrder.ENTITY_NAME,
                    String.valueOf(AdoptionOrder.ENTITY_VERSION),
                    arrayNode
            );
            List<UUID> ids = idsFuture.get();
            List<TechnicalIdResponse> resp = new ArrayList<>();
            for (UUID id : ids) {
                resp.add(new TechnicalIdResponse(id.toString()));
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for createAdoptionOrdersBatch", e);
            return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Failed to create AdoptionOrders batch", e);
            return ResponseEntity.status(500).body(errorBody(e.getMessage()));
        }
    }

    @Operation(summary = "Get AdoptionOrder", description = "Retrieve an AdoptionOrder by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AdoptionOrderGetResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}")
    public ResponseEntity<?> getAdoptionOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    AdoptionOrder.ENTITY_NAME,
                    String.valueOf(AdoptionOrder.ENTITY_VERSION),
                    id
            );
            ObjectNode entityNode = itemFuture.get();
            AdoptionOrderGetResponse resp = new AdoptionOrderGetResponse();
            resp.setTechnicalId(technicalId);
            resp.setEntity(entityNode);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId for getAdoptionOrder", e);
            return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Failed to get AdoptionOrder", e);
            return ResponseEntity.status(500).body(errorBody(e.getMessage()));
        }
    }

    @Operation(summary = "List AdoptionOrders", description = "Retrieve all AdoptionOrder entities (read-only).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdoptionOrder.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listAdoptionOrders() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    AdoptionOrder.ENTITY_NAME,
                    String.valueOf(AdoptionOrder.ENTITY_VERSION)
            );
            ArrayNode nodes = itemsFuture.get();
            return ResponseEntity.ok(nodes);
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Failed to list AdoptionOrders", e);
            return ResponseEntity.status(500).body(errorBody(e.getMessage()));
        }
    }

    @Operation(summary = "Search AdoptionOrders", description = "Search AdoptionOrder entities by condition (basic field filtering).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdoptionOrder.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchAdoptionOrders(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "SearchConditionRequest payload", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    AdoptionOrder.ENTITY_NAME,
                    String.valueOf(AdoptionOrder.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode nodes = filteredItemsFuture.get();
            return ResponseEntity.ok(nodes);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid search condition for searchAdoptionOrders", e);
            return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Failed to search AdoptionOrders", e);
            return ResponseEntity.status(500).body(errorBody(e.getMessage()));
        }
    }

    @Operation(summary = "Update AdoptionOrder", description = "Update an existing AdoptionOrder by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateAdoptionOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "AdoptionOrder payload", required = true,
                    content = @Content(schema = @Schema(implementation = AdoptionOrderRequest.class)))
            @RequestBody AdoptionOrderRequest request) {
        try {
            UUID id = UUID.fromString(technicalId);
            ObjectNode node = (ObjectNode) objectMapper.valueToTree(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    AdoptionOrder.ENTITY_NAME,
                    String.valueOf(AdoptionOrder.ENTITY_VERSION),
                    id,
                    node
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for updateAdoptionOrder", e);
            return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Failed to update AdoptionOrder", e);
            return ResponseEntity.status(500).body(errorBody(e.getMessage()));
        }
    }

    @Operation(summary = "Delete AdoptionOrder", description = "Delete an AdoptionOrder by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}")
    public ResponseEntity<?> deleteAdoptionOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    AdoptionOrder.ENTITY_NAME,
                    String.valueOf(AdoptionOrder.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deletedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId for deleteAdoptionOrder", e);
            return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Failed to delete AdoptionOrder", e);
            return ResponseEntity.status(500).body(errorBody(e.getMessage()));
        }
    }

    @Operation(summary = "Approve AdoptionOrder (admin)", description = "Admin approves an adoption order. This controller only proxies the update payload to the entity service.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/{technicalId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> approveAdoptionOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Admin action payload (e.g. status/approvedDate/notes)", required = false,
                    content = @Content(schema = @Schema(implementation = AdminActionRequest.class)))
            @RequestBody(required = false) AdminActionRequest request) {
        try {
            UUID id = UUID.fromString(technicalId);
            ObjectNode node = request != null ? (ObjectNode) objectMapper.valueToTree(request) : objectMapper.createObjectNode();
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    AdoptionOrder.ENTITY_NAME,
                    String.valueOf(AdoptionOrder.ENTITY_VERSION),
                    id,
                    node
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for approveAdoptionOrder", e);
            return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Failed to approve AdoptionOrder", e);
            return ResponseEntity.status(500).body(errorBody(e.getMessage()));
        }
    }

    @Operation(summary = "Decline AdoptionOrder (admin)", description = "Admin declines an adoption order. This controller only proxies the update payload to the entity service.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/{technicalId}/decline", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> declineAdoptionOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Admin action payload (e.g. status/cancelledDate/notes)", required = false,
                    content = @Content(schema = @Schema(implementation = AdminActionRequest.class)))
            @RequestBody(required = false) AdminActionRequest request) {
        try {
            UUID id = UUID.fromString(technicalId);
            ObjectNode node = request != null ? (ObjectNode) objectMapper.valueToTree(request) : objectMapper.createObjectNode();
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    AdoptionOrder.ENTITY_NAME,
                    String.valueOf(AdoptionOrder.ENTITY_VERSION),
                    id,
                    node
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for declineAdoptionOrder", e);
            return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Failed to decline AdoptionOrder", e);
            return ResponseEntity.status(500).body(errorBody(e.getMessage()));
        }
    }

    @Operation(summary = "Cancel AdoptionOrder", description = "Cancel an adoption order. This controller only proxies the update payload to the entity service.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/{technicalId}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cancelAdoptionOrder(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Cancel payload (e.g. status/cancelledDate/notes)", required = false,
                    content = @Content(schema = @Schema(implementation = AdminActionRequest.class)))
            @RequestBody(required = false) AdminActionRequest request) {
        try {
            UUID id = UUID.fromString(technicalId);
            ObjectNode node = request != null ? (ObjectNode) objectMapper.valueToTree(request) : objectMapper.createObjectNode();
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    AdoptionOrder.ENTITY_NAME,
                    String.valueOf(AdoptionOrder.ENTITY_VERSION),
                    id,
                    node
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for cancelAdoptionOrder", e);
            return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (Exception e) {
            logger.error("Failed to cancel AdoptionOrder", e);
            return ResponseEntity.status(500).body(errorBody(e.getMessage()));
        }
    }

    private ResponseEntity<Map<String, Object>> handleExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        String message = cause != null ? cause.getMessage() : e.getMessage();
        logger.error("ExecutionException in controller", e);
        if (cause instanceof NoSuchElementException) {
            return ResponseEntity.status(404).body(errorBody(message));
        } else if (cause instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().body(errorBody(message));
        } else {
            return ResponseEntity.status(500).body(errorBody(message));
        }
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("error", message != null ? message : "Unknown error");
        m.put("details", Collections.emptyMap());
        return m;
    }

    // DTOs

    @Data
    @Schema(name = "AdoptionOrderRequest", description = "AdoptionOrder request payload")
    public static class AdoptionOrderRequest {
        @Schema(description = "Business id (human readable)", example = "order-900")
        private String id;
        @Schema(description = "Pet business id", example = "pet-123")
        private String petId;
        @Schema(description = "User business id", example = "user-55")
        private String userId;
        @Schema(description = "Order status", example = "requested")
        private String status;
        @Schema(description = "Requested date (ISO-8601)", example = "2025-08-22T11:00:00Z")
        private String requestedDate;
        @Schema(description = "Approved date (ISO-8601)", example = "2025-08-22T12:00:00Z")
        private String approvedDate;
        @Schema(description = "Completed date (ISO-8601)", example = "2025-08-24T15:00:00Z")
        private String completedDate;
        @Schema(description = "Cancelled date (ISO-8601)", example = "2025-08-23T09:00:00Z")
        private String cancelledDate;
        @Schema(description = "Notes", example = "Would prefer home delivery")
        private String notes;
        @Schema(description = "Pickup method", example = "homeDelivery")
        private String pickupMethod;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Assigned technical id", example = "tech-order-0001")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "AdoptionOrderGetResponse", description = "Get response for AdoptionOrder")
    public static class AdoptionOrderGetResponse {
        @Schema(description = "Assigned technical id", example = "tech-order-0001")
        private String technicalId;
        @Schema(description = "Stored entity", implementation = AdoptionOrder.class)
        private ObjectNode entity;
    }

    @Data
    @Schema(name = "AdminActionRequest", description = "Admin action payload for approve/decline/cancel operations")
    public static class AdminActionRequest {
        @Schema(description = "Optional status to set", example = "approved")
        private String status;
        @Schema(description = "Optional approved date (ISO-8601)", example = "2025-08-22T12:00:00Z")
        private String approvedDate;
        @Schema(description = "Optional cancelled date (ISO-8601)", example = "2025-08-23T09:00:00Z")
        private String cancelledDate;
        @Schema(description = "Notes", example = "Admin approved after verification")
        private String notes;
    }
}