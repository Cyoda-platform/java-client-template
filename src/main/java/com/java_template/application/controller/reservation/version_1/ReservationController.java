package com.java_template.application.controller.reservation.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.reservation.version_1.Reservation;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/reservation")
@Tag(name = "Reservation", description = "Reservation entity proxy API (version 1)")
public class ReservationController {

    private static final Logger logger = LoggerFactory.getLogger(ReservationController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReservationController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Reservation", description = "Create a Reservation entity event. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createReservation(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Reservation create payload", required = true,
                    content = @Content(schema = @Schema(implementation = ReservationRequest.class)))
            @RequestBody ReservationRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            Reservation entity = mapRequestToEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Reservation.ENTITY_NAME,
                    Reservation.ENTITY_VERSION,
                    entity
            );
            UUID id = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Bad request on createReservation: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createReservation", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating reservation", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error in createReservation", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Create multiple Reservations", description = "Create multiple Reservation entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createReservationsBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of reservations to create", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReservationRequest.class))))
            @RequestBody List<ReservationRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request list must not be empty");
            }
            List<Reservation> entities = new ArrayList<>();
            for (ReservationRequest r : requests) {
                entities.add(mapRequestToEntity(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Reservation.ENTITY_NAME,
                    Reservation.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<TechnicalIdResponse> result = new ArrayList<>();
            if (ids != null) {
                for (UUID uid : ids) {
                    TechnicalIdResponse tr = new TechnicalIdResponse();
                    tr.setTechnicalId(uid.toString());
                    result.add(tr);
                }
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            logger.warn("Bad request on createReservationsBulk: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createReservationsBulk", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating reservations bulk", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error in createReservationsBulk", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get Reservation by technicalId", description = "Retrieve a reservation by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getReservationById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectMapper mapper = objectMapper;
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(404).body("Reservation not found");
            }
            JsonNode node = (JsonNode) dataPayload.getData();
            ReservationResponse response = mapper.treeToValue(node, ReservationResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            logger.warn("Bad request on getReservationById: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getReservationById", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting reservation", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error in getReservationById", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "List Reservations", description = "Retrieve all reservations (paged not implemented).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReservationResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listReservations() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Reservation.ENTITY_NAME,
                    Reservation.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<ReservationResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    ReservationResponse resp = objectMapper.treeToValue(data, ReservationResponse.class);
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in listReservations", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing reservations", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error in listReservations", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Search Reservations by condition", description = "Search reservations by a single field condition. Example: ?field=cartId&operator=EQUALS&value=cart-1")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReservationResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/search")
    public ResponseEntity<?> searchReservations(
            @RequestParam(name = "field") String field,
            @RequestParam(name = "operator", required = false, defaultValue = "EQUALS") String operator,
            @RequestParam(name = "value") String value) {
        try {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("field is required");
            }
            if (value == null) {
                throw new IllegalArgumentException("value is required");
            }
            // Build a simple JSONPath-based condition as required by SearchConditionRequest
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$." + field, operator, value)
            );
            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    Reservation.ENTITY_NAME,
                    Reservation.ENTITY_VERSION,
                    condition,
                    true
            );
            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            List<ReservationResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    ReservationResponse resp = objectMapper.treeToValue(data, ReservationResponse.class);
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException ex) {
            logger.warn("Bad request on searchReservations: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchReservations", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching reservations", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error in searchReservations", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Update Reservation", description = "Update a Reservation entity by technicalId. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateReservation(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Reservation update payload", required = true,
                    content = @Content(schema = @Schema(implementation = ReservationRequest.class)))
            @RequestBody ReservationRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            Reservation entity = mapRequestToEntity(request);
            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    UUID.fromString(technicalId),
                    entity
            );
            UUID id = updatedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Bad request on updateReservation: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateReservation", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating reservation", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error in updateReservation", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Delete Reservation", description = "Delete a Reservation by technicalId. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteReservation(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedId = entityService.deleteItem(UUID.fromString(technicalId));
            UUID id = deletedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Bad request on deleteReservation: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteReservation", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting reservation", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error in deleteReservation", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // Map request DTO to entity (no business logic)
    private Reservation mapRequestToEntity(ReservationRequest request) {
        Reservation entity = new Reservation();
        entity.setId(request.getId());
        entity.setCartId(request.getCartId());
        entity.setProductId(request.getProductId());
        entity.setWarehouseId(request.getWarehouseId());
        entity.setQty(request.getQty());
        entity.setStatus(request.getStatus());
        entity.setCreatedAt(request.getCreatedAt());
        entity.setExpiresAt(request.getExpiresAt());
        return entity;
    }

    // DTOs

    @Data
    @Schema(name = "ReservationRequest", description = "Reservation create/update request")
    public static class ReservationRequest {
        @Schema(description = "Technical id (string UUID)", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String id;
        @Schema(description = "Cart id", example = "cart-1")
        private String cartId;
        @Schema(description = "Product id", example = "product-1")
        private String productId;
        @Schema(description = "Warehouse id", example = "warehouse-1")
        private String warehouseId;
        @Schema(description = "Quantity reserved", example = "2")
        private Integer qty;
        @Schema(description = "Status", example = "ACTIVE")
        private String status;
        @Schema(description = "Created at ISO-8601", example = "2025-08-28T12:00:00Z")
        private String createdAt;
        @Schema(description = "Expires at ISO-8601", example = "2025-08-28T12:00:30Z")
        private String expiresAt;
    }

    @Data
    @Schema(name = "ReservationResponse", description = "Reservation response payload")
    public static class ReservationResponse {
        @Schema(description = "Technical id (string UUID)", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String id;
        @Schema(description = "Cart id", example = "cart-1")
        private String cartId;
        @Schema(description = "Product id", example = "product-1")
        private String productId;
        @Schema(description = "Warehouse id", example = "warehouse-1")
        private String warehouseId;
        @Schema(description = "Quantity reserved", example = "2")
        private Integer qty;
        @Schema(description = "Status", example = "ACTIVE")
        private String status;
        @Schema(description = "Created at ISO-8601", example = "2025-08-28T12:00:00Z")
        private String createdAt;
        @Schema(description = "Expires at ISO-8601", example = "2025-08-28T12:00:30Z")
        private String expiresAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String technicalId;
    }
}