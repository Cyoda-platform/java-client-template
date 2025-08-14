package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.application.entity.event.version_1.Event;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class BookingValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BookingValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public BookingValidationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcesso rCalculationRequest request = context.getEvent();
        logger.info("Processing Booking for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Booking.class)
            .validate(this::isValidEntity, "Invalid booking state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Booking booking) {
        return booking != null && booking.getBookingStatus() != null && !booking.getBookingStatus().isEmpty();
    }

    private Booking processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Booking> context) {
        Booking booking = context.entity();

        // Basic validations
        if (booking.getEventId() == null || booking.getEventId().isEmpty()) {
            logger.error("Booking must reference a valid eventId");
            throw new IllegalArgumentException("Booking must reference a valid eventId");
        }

        if (booking.getNumberOfTickets() == null || booking.getNumberOfTickets() <= 0) {
            logger.error("Number of tickets must be positive");
            throw new IllegalArgumentException("Number of tickets must be positive");
        }

        if (booking.getBookingDate() == null || booking.getBookingDate().isEmpty()) {
            logger.error("Booking date must be specified");
            throw new IllegalArgumentException("Booking date must be specified");
        }

        // Validate bookingStatus
        String status = booking.getBookingStatus();
        if (!status.equalsIgnoreCase("PENDING") && !status.equalsIgnoreCase("VALIDATED") && !status.equalsIgnoreCase("CONFIRMED") && !status.equalsIgnoreCase("WAITLIST") && !status.equalsIgnoreCase("CANCELLED")) {
            logger.error("Invalid booking status: {}", status);
            throw new IllegalArgumentException("Invalid booking status: " + status);
        }

        // Check event capacity and ticket availability
        try {
            CompletableFuture<ObjectNode> eventFuture = entityService.getItem(
                Event.ENTITY_NAME,
                String.valueOf(Event.ENTITY_VERSION),
                java.util.UUID.fromString(booking.getEventId())
            );
            ObjectNode eventNode = eventFuture.get();

            if (eventNode == null) {
                logger.error("Referenced event does not exist: {}", booking.getEventId());
                throw new IllegalArgumentException("Referenced event does not exist: " + booking.getEventId());
            }

            Integer capacity = eventNode.hasNonNull("capacity") ? eventNode.get("capacity").asInt() : null;
            if (capacity == null) {
                logger.error("Event capacity not specified");
                throw new IllegalArgumentException("Event capacity not specified");
            }

            // Calculate current bookings for this event
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.eventId", "EQUALS", booking.getEventId())
            );
            CompletableFuture<ArrayNode> bookingsFuture = entityService.getItemsByCondition(
                Booking.ENTITY_NAME,
                String.valueOf(Booking.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode bookingsArray = bookingsFuture.get();

            int totalBooked = 0;
            for (int i = 0; i < bookingsArray.size(); i++) {
                ObjectNode bNode = (ObjectNode) bookingsArray.get(i);
                if (bNode.hasNonNull("numberOfTickets") && bNode.hasNonNull("bookingStatus")) {
                    String bStatus = bNode.get("bookingStatus").asText();
                    if (!bStatus.equalsIgnoreCase("CANCELLED")) {
                        totalBooked += bNode.get("numberOfTickets").asInt();
                    }
                }
            }

            int requestedTickets = booking.getNumberOfTickets();
            if (totalBooked + requestedTickets > capacity) {
                logger.warn("Booking exceeds event capacity: requested {} but only {} left", requestedTickets, capacity - totalBooked);
                // Update booking status to WAITLIST
                booking.setBookingStatus("WAITLIST");
            } else {
                booking.setBookingStatus("VALIDATED");
            }

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while validating booking capacity", e);
            throw new RuntimeException("Error while validating booking capacity", e);
        }

        return booking;
    }
}
