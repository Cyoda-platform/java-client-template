package com.java_template.application.controller.booking.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dull proxy controller for Booking entity. All business logic is handled in workflows.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Booking", description = "Booking entity proxy API")
public class BookingController {

    private static final Logger logger = LoggerFactory.getLogger(BookingController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BookingController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Booking", description = "Persist a single Booking entity. Returns technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = AddBookingResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createBooking(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Booking payload",
            required = true,
            content = @Content(schema = @Schema(implementation = AddBookingRequest.class),
                examples = @ExampleObject(value = "{ \"bookingId\": 1, \"firstName\": \"John\", \"lastName\": \"Doe\", \"totalPrice\": 120.0, \"depositPaid\": true, \"bookingDates\": {\"checkin\":\"2025-01-05\",\"checkout\":\"2025-01-08\"} }"))
        )
        @RequestBody AddBookingRequest request
    ) {
        try {
            Booking booking = toBooking(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Booking.ENTITY_NAME,
                String.valueOf(Booking.ENTITY_VERSION),
                booking
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new AddBookingResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while creating booking", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating booking", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Bookings", description = "Persist multiple Booking entities. Returns list of technicalIds.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = BatchAddBookingResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/batch", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createBookingsBatch(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Array of bookings",
            required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AddBookingRequest.class)))
        )
        @RequestBody List<AddBookingRequest> requests
    ) {
        try {
            List<Booking> bookings = new ArrayList<>();
            if (requests != null) {
                for (AddBookingRequest r : requests) {
                    bookings.add(toBooking(r));
                }
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                Booking.ENTITY_NAME,
                String.valueOf(Booking.ENTITY_VERSION),
                bookings
            );
            List<UUID> ids = idsFuture.get();
            List<String> sids = new ArrayList<>();
            if (ids != null) {
                for (UUID u : ids) sids.add(u.toString());
            }
            return ResponseEntity.ok(new BatchAddBookingResponse(sids));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid batch request: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while creating bookings batch", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating bookings batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Booking by technicalId", description = "Retrieve a persisted Booking by its technical id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = GetBookingResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> getBookingById(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId
    ) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Booking.ENTITY_NAME,
                String.valueOf(Booking.ENTITY_VERSION),
                tid
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Booking not found");
            }
            Booking booking = objectMapper.convertValue(node, Booking.class);
            return ResponseEntity.ok(new GetBookingResponse(booking));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId or request: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while getting booking", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while getting booking", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Bookings (with optional filters)", description = "Retrieve bookings. Supports simple filters via query params: from,to,minPrice,maxPrice,depositPaid,customerName")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = BookingsListResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = "application/json")
    public ResponseEntity<?> getBookings(
        @Parameter(description = "Checkin from (ISO date)") @RequestParam(required = false) String from,
        @Parameter(description = "Checkin to (ISO date)") @RequestParam(required = false) String to,
        @Parameter(description = "Minimum total price") @RequestParam(required = false) Double minPrice,
        @Parameter(description = "Maximum total price") @RequestParam(required = false) Double maxPrice,
        @Parameter(description = "Deposit paid") @RequestParam(required = false) Boolean depositPaid,
        @Parameter(description = "Customer name (first or last)") @RequestParam(required = false) String customerName
    ) {
        try {
            ArrayNode itemsNode;
            boolean hasFilter = (from != null) || (to != null) || (minPrice != null) || (maxPrice != null) || (depositPaid != null) || (customerName != null && !customerName.isBlank());
            if (hasFilter) {
                List<Condition> conditions = new ArrayList<>();
                if (from != null && !from.isBlank()) {
                    conditions.add(Condition.of("$.bookingDates.checkin", "GREATER_THAN", from));
                }
                if (to != null && !to.isBlank()) {
                    conditions.add(Condition.of("$.bookingDates.checkin", "LESS_THAN", to));
                }
                if (minPrice != null) {
                    conditions.add(Condition.of("$.totalPrice", "GREATER_THAN", String.valueOf(minPrice)));
                }
                if (maxPrice != null) {
                    conditions.add(Condition.of("$.totalPrice", "LESS_THAN", String.valueOf(maxPrice)));
                }
                if (depositPaid != null) {
                    conditions.add(Condition.of("$.depositPaid", "EQUALS", String.valueOf(depositPaid)));
                }
                if (customerName != null && !customerName.isBlank()) {
                    conditions.add(Condition.of("$.firstName", "IEQUALS", customerName));
                }
                SearchConditionRequest condition = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Booking.ENTITY_NAME,
                    String.valueOf(Booking.ENTITY_VERSION),
                    condition,
                    true
                );
                itemsNode = filteredItemsFuture.get();
            } else {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Booking.ENTITY_NAME,
                    String.valueOf(Booking.ENTITY_VERSION)
                );
                itemsNode = itemsFuture.get();
            }

            List<Booking> bookings = new ArrayList<>();
            if (itemsNode != null) {
                for (int i = 0; i < itemsNode.size(); i++) {
                    ObjectNode node = (ObjectNode) itemsNode.get(i);
                    Booking b = objectMapper.convertValue(node, Booking.class);
                    bookings.add(b);
                }
            }
            return ResponseEntity.ok(new BookingsListResponse(bookings.size(), bookings));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid filter parameter: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while listing bookings", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while listing bookings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Booking", description = "Update a Booking by technical id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = UpdateBookingResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> updateBooking(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Booking payload for update",
            required = true,
            content = @Content(schema = @Schema(implementation = AddBookingRequest.class))
        )
        @RequestBody AddBookingRequest request
    ) {
        try {
            UUID tid = UUID.fromString(technicalId);
            Booking booking = toBooking(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                Booking.ENTITY_NAME,
                String.valueOf(Booking.ENTITY_VERSION),
                tid,
                booking
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new UpdateBookingResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request or technicalId: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while updating booking", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while updating booking", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Booking", description = "Delete a Booking by technical id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = DeleteBookingResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> deleteBooking(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId
    ) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                Booking.ENTITY_NAME,
                String.valueOf(Booking.ENTITY_VERSION),
                tid
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new DeleteBookingResponse(deletedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while deleting booking", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? ee.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while deleting booking", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Helper to convert DTO to entity (simple mapping - no business logic)
    private Booking toBooking(AddBookingRequest req) {
        if (req == null) throw new IllegalArgumentException("Request body is required");
        Booking booking = new Booking();
        booking.setBookingId(req.getBookingId());
        booking.setFirstName(req.getFirstName());
        booking.setLastName(req.getLastName());
        booking.setAdditionalNeeds(req.getAdditionalNeeds());
        Booking.BookingDates dates = new Booking.BookingDates();
        if (req.getBookingDates() != null) {
            dates.setCheckin(req.getBookingDates().getCheckin());
            dates.setCheckout(req.getBookingDates().getCheckout());
        }
        booking.setBookingDates(dates);
        booking.setDepositPaid(req.getDepositPaid());
        booking.setTotalPrice(req.getTotalPrice());
        booking.setPersistedAt(req.getPersistedAt());
        booking.setSource(req.getSource());
        return booking;
    }

    // ---------------- DTOs ----------------

    @Data
    @Schema(description = "Request to add or update a Booking")
    public static class AddBookingRequest {
        @Schema(description = "Provider booking identifier", example = "1")
        private Integer bookingId;

        @Schema(description = "Customer first name", example = "John")
        private String firstName;

        @Schema(description = "Customer last name", example = "Doe")
        private String lastName;

        @Schema(description = "Booking total price", example = "120.0")
        private Double totalPrice;

        @Schema(description = "Whether deposit was paid", example = "true")
        private Boolean depositPaid;

        @Schema(description = "Booking dates")
        private BookingDatesDto bookingDates;

        @Schema(description = "Optional notes", example = "late arrival")
        private String additionalNeeds;

        @Schema(description = "External source identifier", example = "external-system")
        private String source;

        @Schema(description = "ISO timestamp when stored", example = "2025-01-05T12:00:00Z")
        private String persistedAt;

        @Data
        @Schema(description = "BookingDates")
        public static class BookingDatesDto {
            @Schema(description = "Check-in date (ISO)", example = "2025-01-05")
            private String checkin;
            @Schema(description = "Check-out date (ISO)", example = "2025-01-08")
            private String checkout;
        }
    }

    @Data
    @Schema(description = "Response after adding a Booking")
    public static class AddBookingResponse {
        @Schema(description = "Technical id of the persisted entity")
        private String technicalId;

        public AddBookingResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(description = "Response after adding multiple Bookings")
    public static class BatchAddBookingResponse {
        @Schema(description = "Technical ids of persisted entities")
        private List<String> technicalIds;

        public BatchAddBookingResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(description = "Response for a retrieved Booking")
    public static class GetBookingResponse {
        @Schema(description = "Booking entity")
        private Booking booking;

        public GetBookingResponse(Booking booking) {
            this.booking = booking;
        }
    }

    @Data
    @Schema(description = "List response for Bookings")
    public static class BookingsListResponse {
        @Schema(description = "Number of bookings returned", example = "1")
        private Integer count;

        @Schema(description = "List of bookings")
        private List<Booking> bookings;

        public BookingsListResponse(Integer count, List<Booking> bookings) {
            this.count = count;
            this.bookings = bookings;
        }
    }

    @Data
    @Schema(description = "Response after updating a Booking")
    public static class UpdateBookingResponse {
        @Schema(description = "Technical id of the updated entity")
        private String technicalId;

        public UpdateBookingResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(description = "Response after deleting a Booking")
    public static class DeleteBookingResponse {
        @Schema(description = "Technical id of the deleted entity")
        private String technicalId;

        public DeleteBookingResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}