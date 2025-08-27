package com.java_template.application.controller.reservation.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.reservation.version_1.Reservation;
import com.java_template.common.service.EntityService;
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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/entity/reservation/v1")
@Tag(name = "Reservation", description = "Reservation entity API (proxy to entity service)")
public class ReservationController {

    private static final Logger logger = LoggerFactory.getLogger(ReservationController.class);

    private final EntityService entityService;

    public ReservationController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Reservation", description = "Creates a Reservation entity and returns its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createReservation(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Reservation payload", required = true,
                    content = @Content(schema = @Schema(implementation = ReservationRequest.class)))
            @RequestBody ReservationRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            Reservation entity = mapToEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Reservation.ENTITY_NAME,
                    String.valueOf(Reservation.ENTITY_VERSION),
                    entity
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createReservation request", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Unexpected error in createReservation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Reservations", description = "Creates multiple Reservation entities and returns their technicalIds")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createReservationsBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of reservations", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReservationRequest.class))))
            @RequestBody List<ReservationRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request list is required");
            List<Reservation> entities = requests.stream().map(this::mapToEntity).collect(Collectors.toList());
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Reservation.ENTITY_NAME,
                    String.valueOf(Reservation.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<TechnicalIdResponse> responses = ids.stream()
                    .map(uuid -> new TechnicalIdResponse(uuid.toString()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createReservationsBulk request", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Unexpected error in createReservationsBulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Reservation", description = "Retrieves a Reservation by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = Reservation.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getReservation(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Reservation.ENTITY_NAME,
                    String.valueOf(Reservation.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid getReservation request", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Unexpected error in getReservation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Reservations", description = "Retrieves all Reservation entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Reservation.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listReservations() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Reservation.ENTITY_NAME,
                    String.valueOf(Reservation.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Unexpected error in listReservations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Reservations", description = "Search Reservations by simple conditions (in-memory filtering)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Reservation.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchReservations(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Reservation.ENTITY_NAME,
                    String.valueOf(Reservation.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid searchReservations request", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Unexpected error in searchReservations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Reservation", description = "Updates a Reservation entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateReservation(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Reservation payload", required = true,
                    content = @Content(schema = @Schema(implementation = ReservationRequest.class)))
            @RequestBody ReservationRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");
            Reservation entity = mapToEntity(request);
            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    Reservation.ENTITY_NAME,
                    String.valueOf(Reservation.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    entity
            );
            UUID id = updatedId.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid updateReservation request", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Unexpected error in updateReservation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Reservation", description = "Deletes a Reservation entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteReservation(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    Reservation.ENTITY_NAME,
                    String.valueOf(Reservation.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID id = deletedId.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid deleteReservation request", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Unexpected error in deleteReservation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private Reservation mapToEntity(ReservationRequest req) {
        Reservation r = new Reservation();
        if (req.getReservationId() != null) r.setReservationId(req.getReservationId());
        if (req.getCartId() != null) r.setCartId(req.getCartId());
        if (req.getReservationBatchId() != null) r.setReservationBatchId(req.getReservationBatchId());
        r.setSku(req.getSku());
        r.setQty(req.getQty());
        if (req.getStatus() != null) r.setStatus(req.getStatus());
        if (req.getExpiresAt() != null) r.setExpiresAt(req.getExpiresAt());
        return r;
    }

    private ResponseEntity<String> handleExecutionException(ExecutionException ee) {
        Throwable cause = ee.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found", cause);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument in execution", cause);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
        } else {
            logger.error("Execution exception", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        }
    }

    // Static DTOs for requests/responses

    @Data
    @Schema(name = "ReservationRequest", description = "Payload to create or update a Reservation")
    public static class ReservationRequest {
        @Schema(description = "Technical reservation id (serialized UUID)", required = false)
        private String reservationId;
        @Schema(description = "Cart id (serialized UUID)", required = true)
        private String cartId;
        @Schema(description = "Reservation batch id", required = true)
        private String reservationBatchId;
        @Schema(description = "SKU", required = true)
        private String sku;
        @Schema(description = "Quantity", required = true)
        private Integer qty;
        @Schema(description = "Status", required = false, example = "ACTIVE")
        private String status;
        @Schema(description = "Expiration timestamp ISO-8601", required = false)
        private String expiresAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing created/updated/deleted technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id (UUID string)")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}