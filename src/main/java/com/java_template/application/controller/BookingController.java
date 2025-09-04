package com.java_template.application.controller;

import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private static final Logger logger = LoggerFactory.getLogger(BookingController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping("/fetch")
    public ResponseEntity<Map<String, Object>> fetchBooking(@RequestBody Map<String, Object> request) {
        try {
            Integer bookingId = (Integer) request.get("bookingId");
            if (bookingId == null) {
                return createErrorResponse("Booking ID is required", HttpStatus.BAD_REQUEST);
            }

            // Create a new booking entity with the provided booking ID
            Booking booking = new Booking();
            booking.setBookingId(bookingId);

            // Save the booking entity (this will trigger the fetch_booking transition)
            EntityResponse<Booking> savedBooking = entityService.save(booking);

            Map<String, Object> responseData = createBookingResponseData(savedBooking);
            return createSuccessResponse(responseData, "Booking fetch initiated successfully");

        } catch (Exception e) {
            logger.error("Error fetching booking: {}", e.getMessage(), e);
            return createErrorResponse("Failed to fetch booking: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllBookings() {
        try {
            List<EntityResponse<Booking>> bookings = entityService.findAll(Booking.class);
            
            List<Map<String, Object>> bookingData = bookings.stream()
                .map(this::createBookingResponseData)
                .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", bookingData);
            response.put("count", bookingData.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error retrieving bookings: {}", e.getMessage(), e);
            return createErrorResponse("Failed to retrieve bookings: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getBookingById(@PathVariable String id) {
        try {
            UUID bookingUuid = UUID.fromString(id);
            EntityResponse<Booking> booking = entityService.getById(bookingUuid, Booking.class);

            if (booking == null) {
                return createErrorResponse("Booking not found", HttpStatus.NOT_FOUND);
            }

            Map<String, Object> responseData = createBookingResponseData(booking);
            return createSuccessResponse(responseData, null);

        } catch (IllegalArgumentException e) {
            return createErrorResponse("Invalid booking ID format", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Error retrieving booking {}: {}", id, e.getMessage(), e);
            return createErrorResponse("Failed to retrieve booking: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}/state")
    public ResponseEntity<Map<String, Object>> updateBookingState(@PathVariable String id, @RequestBody Map<String, Object> request) {
        try {
            UUID bookingUuid = UUID.fromString(id);
            String transition = (String) request.get("transition");

            if (transition == null) {
                return createErrorResponse("Transition is required", HttpStatus.BAD_REQUEST);
            }

            // Get the current booking
            EntityResponse<Booking> currentBooking = entityService.getById(bookingUuid, Booking.class);
            if (currentBooking == null) {
                return createErrorResponse("Booking not found", HttpStatus.NOT_FOUND);
            }

            // Update the booking with the specified transition
            EntityResponse<Booking> updatedBooking = entityService.update(bookingUuid, currentBooking.getData(), transition);

            Map<String, Object> responseData = createBookingResponseData(updatedBooking);
            return createSuccessResponse(responseData, "Booking state updated successfully");

        } catch (IllegalArgumentException e) {
            return createErrorResponse("Invalid booking ID format", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Error updating booking state {}: {}", id, e.getMessage(), e);
            return createErrorResponse("Failed to update booking state: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryFailedBooking(@PathVariable String id) {
        try {
            UUID bookingUuid = UUID.fromString(id);

            // Get the current booking
            EntityResponse<Booking> currentBooking = entityService.getById(bookingUuid, Booking.class);
            if (currentBooking == null) {
                return createErrorResponse("Booking not found", HttpStatus.NOT_FOUND);
            }

            // Update the booking with retry_fetch transition
            EntityResponse<Booking> updatedBooking = entityService.update(bookingUuid, currentBooking.getData(), "retry_fetch");

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("id", updatedBooking.getId().toString());
            responseData.put("bookingId", updatedBooking.getData().getBookingId());
            responseData.put("state", updatedBooking.getState());

            return createSuccessResponse(responseData, "Booking retry initiated");

        } catch (IllegalArgumentException e) {
            return createErrorResponse("Invalid booking ID format", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Error retrying booking {}: {}", id, e.getMessage(), e);
            return createErrorResponse("Failed to retry booking: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> createBookingResponseData(EntityResponse<Booking> bookingResponse) {
        Map<String, Object> data = new HashMap<>();
        Booking booking = bookingResponse.getData();
        
        data.put("id", bookingResponse.getId() != null ? bookingResponse.getId().toString() : null);
        data.put("bookingId", booking.getBookingId());
        data.put("firstname", booking.getFirstname());
        data.put("lastname", booking.getLastname());
        data.put("totalprice", booking.getTotalprice());
        data.put("depositpaid", booking.getDepositpaid());
        data.put("checkin", booking.getCheckin());
        data.put("checkout", booking.getCheckout());
        data.put("additionalneeds", booking.getAdditionalneeds());
        data.put("retrievedAt", booking.getRetrievedAt());
        data.put("state", bookingResponse.getState());
        
        return data;
    }

    private ResponseEntity<Map<String, Object>> createSuccessResponse(Object data, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        if (message != null) {
            response.put("message", message);
        }
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return ResponseEntity.status(status).body(response);
    }
}
