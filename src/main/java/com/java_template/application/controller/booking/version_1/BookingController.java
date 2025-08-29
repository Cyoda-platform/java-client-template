package com.java_template.application.controller.booking.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.application.entity.booking.version_1.Booking;
import org.cyoda.cloud.api.event.common.DataPayload;
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

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

@RestController
@RequestMapping("/booking/v1")
@Tag(name = "Booking", description = "Booking entity endpoints (version 1) - proxy to EntityService")
public class BookingController {

    private static final Logger logger = LoggerFactory.getLogger(BookingController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public BookingController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create a booking", description = "Persist a single Booking entity via EntityService. Controller does not apply business logic.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddSingleResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/bookings", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addBooking(@org.springframework.web.bind.annotation.RequestBody AddBookingRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Booking entity = toEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Booking.ENTITY_NAME,
                    Booking.ENTITY_VERSION,
                    entity
            );
            UUID id = idFuture.get();
            AddSingleResponse resp = new AddSingleResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution exception while adding booking", ee);
                return ResponseEntity.status(500).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding booking", ie);
            return ResponseEntity.status(500).body("Internal error");
        } catch (Exception ex) {
            logger.error("Unexpected error while adding booking", ex);
            return ResponseEntity.status(500).body("Internal error");
        }
    }

    @Operation(summary = "Add multiple bookings", description = "Persist multiple Booking entities via EntityService.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AddMultipleResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/bookings/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addBookingsBatch(@org.springframework.web.bind.annotation.RequestBody List<AddBookingRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body must be a non-empty array");
            }
            List<Booking> entities = new ArrayList<>();
            for (AddBookingRequest r : requests) {
                entities.add(toEntity(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Booking.ENTITY_NAME,
                    Booking.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();
            AddMultipleResponse resp = new AddMultipleResponse();
            List<String> technicalIds = new ArrayList<>();
            if (ids != null) {
                for (UUID id : ids) technicalIds.add(id.toString());
            }
            resp.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid batch request: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution exception while adding bookings batch", ee);
                return ResponseEntity.status(500).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding bookings batch", ie);
            return ResponseEntity.status(500).body("Internal error");
        } catch (Exception ex) {
            logger.error("Unexpected error while adding bookings batch", ex);
            return ResponseEntity.status(500).body("Internal error");
        }
    }

    @Operation(summary = "Get a booking", description = "Retrieve a single Booking by technicalId (UUID).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BookingResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/bookings/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getBooking(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(404).body("Booking not found");
            }
            BookingResponse resp = objectMapper.treeToValue(dataPayload.getData(), BookingResponse.class);
            // attach metadata technicalId if not present
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid input for getBooking: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution exception while retrieving booking", ee);
                return ResponseEntity.status(500).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving booking", ie);
            return ResponseEntity.status(500).body("Internal error");
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving booking", ex);
            return ResponseEntity.status(500).body("Internal error");
        }
    }

    @Operation(summary = "Query bookings", description = "Retrieve multiple persisted Bookings. Supports basic filtering and pagination.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BookingListResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/bookings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> queryBookings(
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "minPrice", required = false) Double minPrice,
            @RequestParam(value = "maxPrice", required = false) Double maxPrice,
            @RequestParam(value = "depositPaid", required = false) Boolean depositPaid,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "pageNumber", required = false) Integer pageNumber
    ) {
        try {
            List<DataPayload> dataPayloads;
            boolean hasFilters = (dateFrom != null) || (dateTo != null) || (minPrice != null) || (maxPrice != null) || (depositPaid != null);

            if (hasFilters) {
                List<Condition> conditions = new ArrayList<>();
                if (dateFrom != null) {
                    conditions.add(Condition.of("$.checkin", "GREATER_THAN", dateFrom));
                }
                if (dateTo != null) {
                    conditions.add(Condition.of("$.checkin", "LESS_THAN", dateTo));
                }
                if (minPrice != null) {
                    conditions.add(Condition.of("$.totalprice", "GREATER_THAN", String.valueOf(minPrice)));
                }
                if (maxPrice != null) {
                    conditions.add(Condition.of("$.totalprice", "LESS_THAN", String.valueOf(maxPrice)));
                }
                if (depositPaid != null) {
                    conditions.add(Condition.of("$.depositpaid", "EQUALS", String.valueOf(depositPaid)));
                }
                SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
                CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                        Booking.ENTITY_NAME,
                        Booking.ENTITY_VERSION,
                        conditionRequest,
                        true
                );
                dataPayloads = filteredItemsFuture.get();
            } else {
                CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                        Booking.ENTITY_NAME,
                        Booking.ENTITY_VERSION,
                        pageSize,
                        pageNumber,
                        null
                );
                dataPayloads = itemsFuture.get();
            }

            List<BookingResponse> items = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload == null || payload.getData() == null) continue;
                    BookingResponse resp = objectMapper.treeToValue(payload.getData(), BookingResponse.class);
                    // attempt to get technical id from meta if present
                    if (payload.getMeta() != null && payload.getMeta().has("entityId")) {
                        resp.setTechnicalId(payload.getMeta().get("entityId").asText());
                    }
                    items.add(resp);
                }
            }

            BookingListResponse listResp = new BookingListResponse();
            listResp.setItems(items);
            listResp.setPage(Objects.requireNonNullElse(pageNumber, 1));
            listResp.setPageSize(Objects.requireNonNullElse(pageSize, items.size()));
            listResp.setTotal(items.size());
            return ResponseEntity.ok(listResp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid query params for queryBookings: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution exception while querying bookings", ee);
                return ResponseEntity.status(500).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while querying bookings", ie);
            return ResponseEntity.status(500).body("Internal error");
        } catch (Exception ex) {
            logger.error("Unexpected error while querying bookings", ex);
            return ResponseEntity.status(500).body("Internal error");
        }
    }

    @Operation(summary = "Update a booking", description = "Update a persisted Booking by technicalId via EntityService.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(value = "/bookings/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateBooking(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @org.springframework.web.bind.annotation.RequestBody AddBookingRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            Booking entity = toEntity(request);
            CompletableFuture<java.util.UUID> updatedFuture = entityService.updateItem(
                    UUID.fromString(technicalId),
                    entity
            );
            UUID updatedId = updatedFuture.get();
            UpdateResponse resp = new UpdateResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid input for updateBooking: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution exception while updating booking", ee);
                return ResponseEntity.status(500).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating booking", ie);
            return ResponseEntity.status(500).body("Internal error");
        } catch (Exception ex) {
            logger.error("Unexpected error while updating booking", ex);
            return ResponseEntity.status(500).body("Internal error");
        }
    }

    @Operation(summary = "Delete a booking", description = "Delete a Booking by technicalId via EntityService.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(value = "/bookings/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteBooking(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deletedFuture.get();
            DeleteResponse resp = new DeleteResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid input for deleteBooking: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution exception while deleting booking", ee);
                return ResponseEntity.status(500).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting booking", ie);
            return ResponseEntity.status(500).body("Internal error");
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting booking", ex);
            return ResponseEntity.status(500).body("Internal error");
        }
    }

    // Utility to convert AddBookingRequest to Booking entity
    private Booking toEntity(AddBookingRequest req) {
        Booking b = new Booking();
        b.setBookingId(req.getBookingId());
        b.setFirstname(req.getFirstname());
        b.setLastname(req.getLastname());
        b.setCheckin(req.getCheckin());
        b.setCheckout(req.getCheckout());
        b.setDepositpaid(req.getDepositpaid());
        b.setTotalprice(req.getTotalprice());
        b.setAdditionalneeds(req.getAdditionalneeds());
        b.setPersistedAt(req.getPersistedAt());
        b.setSource(req.getSource());
        return b;
    }

    // Static DTOs for requests/responses

    @Data
    @Schema(name = "AddBookingRequest", description = "Request payload to create/update a Booking")
    public static class AddBookingRequest {
        @Schema(description = "ID from Restful Booker API", example = "1")
        private Integer bookingId;

        @Schema(description = "Guest first name", example = "John")
        private String firstname;

        @Schema(description = "Guest last name", example = "Doe")
        private String lastname;

        @Schema(description = "Check-in date (ISO yyyy-MM-dd)", example = "2025-06-05")
        private String checkin;

        @Schema(description = "Check-out date (ISO yyyy-MM-dd)", example = "2025-06-07")
        private String checkout;

        @Schema(description = "Deposit paid", example = "true")
        private Boolean depositpaid;

        @Schema(description = "Total booking price", example = "200.0")
        private Double totalprice;

        @Schema(description = "Additional needs/notes", example = "Breakfast")
        private String additionalneeds;

        @Schema(description = "Timestamp when persisted (ISO)", example = "2025-06-05T10:00:00Z")
        private String persistedAt;

        @Schema(description = "Source of ingestion", example = "RestfulBooker")
        private String source;
    }

    @Data
    @Schema(name = "BookingResponse", description = "Representation of a persisted Booking")
    public static class BookingResponse {
        @Schema(description = "Technical ID (UUID)", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        private String technicalId;

        @Schema(description = "ID from Restful Booker API", example = "1")
        private Integer bookingId;

        @Schema(description = "Guest first name", example = "John")
        private String firstname;

        @Schema(description = "Guest last name", example = "Doe")
        private String lastname;

        @Schema(description = "Check-in date (ISO yyyy-MM-dd)", example = "2025-06-05")
        private String checkin;

        @Schema(description = "Check-out date (ISO yyyy-MM-dd)", example = "2025-06-07")
        private String checkout;

        @Schema(description = "Deposit paid", example = "true")
        private Boolean depositpaid;

        @Schema(description = "Total booking price", example = "200.0")
        private Double totalprice;

        @Schema(description = "Additional needs/notes", example = "Breakfast")
        private String additionalneeds;

        @Schema(description = "Timestamp when persisted (ISO)", example = "2025-06-05T10:00:00Z")
        private String persistedAt;

        @Schema(description = "Source of ingestion", example = "RestfulBooker")
        private String source;
    }

    @Data
    @Schema(name = "AddSingleResponse", description = "Response when a single entity is created")
    public static class AddSingleResponse {
        @Schema(description = "Technical ID (UUID) of created entity")
        private String technicalId;
    }

    @Data
    @Schema(name = "AddMultipleResponse", description = "Response when multiple entities are created")
    public static class AddMultipleResponse {
        @Schema(description = "List of technical IDs (UUIDs) of created entities")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "UpdateResponse", description = "Response when an entity is updated")
    public static class UpdateResponse {
        @Schema(description = "Technical ID (UUID) of updated entity")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteResponse", description = "Response when an entity is deleted")
    public static class DeleteResponse {
        @Schema(description = "Technical ID (UUID) of deleted entity")
        private String technicalId;
    }

    @Data
    @Schema(name = "BookingListResponse", description = "Paged list response for bookings")
    public static class BookingListResponse {
        @Schema(description = "List of bookings")
        private List<BookingResponse> items;

        @Schema(description = "Total items returned")
        private Integer total;

        @Schema(description = "Page number")
        private Integer page;

        @Schema(description = "Page size")
        private Integer pageSize;
    }
}