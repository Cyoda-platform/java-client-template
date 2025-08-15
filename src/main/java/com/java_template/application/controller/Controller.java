package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.event.version_1.Event;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.application.entity.ticket.version_1.Ticket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Controller", description = "Event, Booking, and Ticket API Controller")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // Event endpoints
    @Operation(summary = "Create Event", description = "Creates a new Event and triggers Event workflow")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Event created successfully",
            content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/events")
    public ResponseEntity<?> createEvent(@RequestBody Event request) {
        try {
            UUID id = entityService.addItem(
                Event.ENTITY_NAME,
                String.valueOf(Event.ENTITY_VERSION),
                request
            ).get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception during createEvent", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected exception during createEvent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Get Event by technicalId", description = "Retrieves Event details by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Event found",
            content = @Content(schema = @Schema(implementation = Event.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Event not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/events/{technicalId}")
    public ResponseEntity<?> getEventById(
        @Parameter(name = "technicalId", description = "Technical ID of the Event") @PathVariable String technicalId) {
        try {
            ObjectNode event = (ObjectNode) entityService.getItem(
                Event.ENTITY_NAME,
                String.valueOf(Event.ENTITY_VERSION),
                UUID.fromString(technicalId)
            ).get();
            return ResponseEntity.ok(event);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception during getEventById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected exception during getEventById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    // Booking endpoints
    @Operation(summary = "Create Booking", description = "Creates a new Booking and triggers Booking workflow")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Booking created successfully",
            content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/bookings")
    public ResponseEntity<?> createBooking(@RequestBody Booking request) {
        try {
            UUID id = entityService.addItem(
                Booking.ENTITY_NAME,
                String.valueOf(Booking.ENTITY_VERSION),
                request
            ).get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception during createBooking", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected exception during createBooking", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Get Booking by technicalId", description = "Retrieves Booking details by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Booking found",
            content = @Content(schema = @Schema(implementation = Booking.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Booking not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/bookings/{technicalId}")
    public ResponseEntity<?> getBookingById(
        @Parameter(name = "technicalId", description = "Technical ID of the Booking") @PathVariable String technicalId) {
        try {
            ObjectNode booking = (ObjectNode) entityService.getItem(
                Booking.ENTITY_NAME,
                String.valueOf(Booking.ENTITY_VERSION),
                UUID.fromString(technicalId)
            ).get();
            return ResponseEntity.ok(booking);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception during getBookingById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected exception during getBookingById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    // Ticket endpoints
    @Operation(summary = "Create Ticket", description = "Creates a new Ticket and triggers Ticket workflow")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ticket created successfully",
            content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/tickets")
    public ResponseEntity<?> createTicket(@RequestBody Ticket request) {
        try {
            UUID id = entityService.addItem(
                Ticket.ENTITY_NAME,
                String.valueOf(Ticket.ENTITY_VERSION),
                request
            ).get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception during createTicket", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected exception during createTicket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Get Ticket by technicalId", description = "Retrieves Ticket details by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ticket found",
            content = @Content(schema = @Schema(implementation = Ticket.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Ticket not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/tickets/{technicalId}")
    public ResponseEntity<?> getTicketById(
        @Parameter(name = "technicalId", description = "Technical ID of the Ticket") @PathVariable String technicalId) {
        try {
            ObjectNode ticket = (ObjectNode) entityService.getItem(
                Ticket.ENTITY_NAME,
                String.valueOf(Ticket.ENTITY_VERSION),
                UUID.fromString(technicalId)
            ).get();
            return ResponseEntity.ok(ticket);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception during getTicketById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected exception during getTicketById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity")
        private final String technicalId;
    }
}
