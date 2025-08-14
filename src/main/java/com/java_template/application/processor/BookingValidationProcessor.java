package com.java_template.application.processor;

import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.application.entity.event.version_1.Event;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Component
public class BookingValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BookingValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public BookingValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
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

        // Business logic:
        // Validate that the booking has a valid event reference and number of tickets is positive
        if (booking.getEventId() == null || booking.getEventId().isEmpty()) {
            logger.error("Booking must reference a valid eventId");
            throw new IllegalArgumentException("Booking must reference a valid eventId");
        }

        if (booking.getNumberOfTickets() == null || booking.getNumberOfTickets() <= 0) {
            logger.error("Number of tickets must be positive");
            throw new IllegalArgumentException("Number of tickets must be positive");
        }

        // Validate bookingDate is present
        if (booking.getBookingDate() == null || booking.getBookingDate().isEmpty()) {
            logger.error("Booking date must be specified");
            throw new IllegalArgumentException("Booking date must be specified");
        }

        // Validate bookingStatus is one of expected statuses
        String status = booking.getBookingStatus();
        if (!status.equalsIgnoreCase("PENDING") && !status.equalsIgnoreCase("VALIDATED") && !status.equalsIgnoreCase("CONFIRMED") && !status.equalsIgnoreCase("WAITLIST") && !status.equalsIgnoreCase("CANCELLED")) {
            logger.error("Invalid booking status: {}", status);
            throw new IllegalArgumentException("Invalid booking status: " + status);
        }

        // Additional validation can be added here

        return booking;
    }
}
