package com.java_template.application.processor;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Booking.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Booking entity) {
        return entity != null && entity.isValid();
    }

    private Booking processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Booking> context) {
        Booking entity = context.entity();

        // Normalize and trim string fields
        if (entity.getFirstname() != null) {
            String f = entity.getFirstname().trim();
            if (!f.isEmpty()) {
                entity.setFirstname(capitalize(f));
            } else {
                entity.setFirstname(f);
            }
        }
        if (entity.getLastname() != null) {
            String l = entity.getLastname().trim();
            if (!l.isEmpty()) {
                entity.setLastname(capitalize(l));
            } else {
                entity.setLastname(l);
            }
        }
        if (entity.getAdditionalneeds() != null) {
            entity.setAdditionalneeds(entity.getAdditionalneeds().trim());
        }
        if (entity.getSource() != null) {
            entity.setSource(entity.getSource().trim());
        }

        // Ensure persistedAt is set (use current instant if missing)
        if (entity.getPersistedAt() == null || entity.getPersistedAt().isBlank()) {
            entity.setPersistedAt(Instant.now().toString());
        }

        // Validate and normalize date order: if checkin > checkout then swap them (to keep consistent)
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        LocalDate checkinDate = null;
        LocalDate checkoutDate = null;
        boolean parsedCheckin = false;
        boolean parsedCheckout = false;

        try {
            if (entity.getCheckin() != null && !entity.getCheckin().isBlank()) {
                checkinDate = LocalDate.parse(entity.getCheckin().trim(), fmt);
                parsedCheckin = true;
            }
        } catch (DateTimeParseException ex) {
            logger.warn("Failed to parse checkin date '{}' for bookingId {}: {}", entity.getCheckin(), entity.getBookingId(), ex.getMessage());
            // leave as-is; do not fail the processor here
        }

        try {
            if (entity.getCheckout() != null && !entity.getCheckout().isBlank()) {
                checkoutDate = LocalDate.parse(entity.getCheckout().trim(), fmt);
                parsedCheckout = true;
            }
        } catch (DateTimeParseException ex) {
            logger.warn("Failed to parse checkout date '{}' for bookingId {}: {}", entity.getCheckout(), entity.getBookingId(), ex.getMessage());
            // leave as-is; do not fail the processor here
        }

        if (parsedCheckin && parsedCheckout && checkinDate != null && checkoutDate != null) {
            if (checkinDate.isAfter(checkoutDate)) {
                logger.info("checkin is after checkout for bookingId {}. Swapping dates to maintain consistency.", entity.getBookingId());
                // swap values to ensure checkin <= checkout
                String originalCheckin = entity.getCheckin();
                entity.setCheckin(entity.getCheckout());
                entity.setCheckout(originalCheckin);
            } else {
                // normalize to trimmed ISO format (in case input had extra spaces)
                entity.setCheckin(checkinDate.format(fmt));
                entity.setCheckout(checkoutDate.format(fmt));
            }
        } else {
            // If one of the dates parsed and the other didn't, attempt to normalize the parsed one
            if (parsedCheckin && checkinDate != null) {
                entity.setCheckin(checkinDate.format(fmt));
            }
            if (parsedCheckout && checkoutDate != null) {
                entity.setCheckout(checkoutDate.format(fmt));
            }
        }

        // Ensure numeric constraints (defensive, though isValid already checked)
        if (entity.getTotalprice() != null && entity.getTotalprice() < 0) {
            logger.warn("Negative totalprice detected for bookingId {}. Setting to 0.0", entity.getBookingId());
            entity.setTotalprice(0.0);
        }

        // Ensure depositpaid is not null (isValid enforces this) - nothing to change here

        return entity;
    }

    private String capitalize(String input) {
        if (input == null || input.isBlank()) return input;
        String trimmed = input.trim();
        if (trimmed.length() == 1) return trimmed.toUpperCase();
        return trimmed.substring(0,1).toUpperCase() + trimmed.substring(1);
    }
}