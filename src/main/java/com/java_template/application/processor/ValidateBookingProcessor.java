package com.java_template.application.processor;

import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class ValidateBookingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateBookingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateBookingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Booking for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Booking.class)
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

        // Business rules:
        // 1. If bookingDates.checkin is null or totalPrice is null -> mark INVALID
        // 2. If checkin >= checkout -> mark INVALID
        // 3. Else mark VALIDATED
        try {
            if (entity == null) {
                logger.warn("Booking entity is null in processing context");
                return entity;
            }

            boolean invalid = false;

            if (entity.getTotalPrice() == null) {
                logger.debug("Booking {} marked INVALID: totalPrice is null", entity.getBookingId());
                invalid = true;
            }

            if (entity.getBookingDates() == null
                || entity.getBookingDates().getCheckin() == null
                || entity.getBookingDates().getCheckin().isBlank()
                || entity.getBookingDates().getCheckout() == null
                || entity.getBookingDates().getCheckout().isBlank()) {
                logger.debug("Booking {} marked INVALID: missing checkin/checkout", entity.getBookingId());
                invalid = true;
            }

            if (!invalid) {
                // parse dates and compare
                try {
                    LocalDate checkin = LocalDate.parse(entity.getBookingDates().getCheckin());
                    LocalDate checkout = LocalDate.parse(entity.getBookingDates().getCheckout());
                    if (!checkin.isBefore(checkout)) {
                        logger.debug("Booking {} marked INVALID: checkin >= checkout ({} >= {})", entity.getBookingId(), checkin, checkout);
                        invalid = true;
                    }
                } catch (DateTimeParseException e) {
                    logger.debug("Booking {} marked INVALID: date parse error - {}", entity.getBookingId(), e.getMessage());
                    invalid = true;
                }
            }

            if (invalid) {
                // mark entity as invalid by setting source marker to "INVALID"
                // (Do not call external services or update other entities here)
                entity.setSource("INVALID");
                logger.info("Booking {} set to INVALID", entity.getBookingId());
            } else {
                // mark validated - downstream StoreBookingProcessor will persist and set persistedAt
                entity.setSource("VALIDATED");
                logger.info("Booking {} set to VALIDATED", entity.getBookingId());
            }

        } catch (Exception ex) {
            logger.error("Error while validating booking: {}", ex.getMessage(), ex);
            // On unexpected error, mark as INVALID to prevent further processing
            if (entity != null) {
                entity.setSource("INVALID");
            }
        }

        return entity;
    }
}