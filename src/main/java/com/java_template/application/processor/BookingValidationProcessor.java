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

import java.util.ArrayList;
import java.util.List;

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
        logger.info("Processing Booking validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Booking.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Booking entity) {
        return entity != null;
    }

    private Booking processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Booking> context) {
        Booking entity = context.entity();
        
        List<String> validationErrors = new ArrayList<>();
        
        // Validate required fields
        if (entity.getFirstname() == null || entity.getFirstname().trim().isEmpty()) {
            validationErrors.add("First name is required");
        }
        
        if (entity.getLastname() == null || entity.getLastname().trim().isEmpty()) {
            validationErrors.add("Last name is required");
        }
        
        if (entity.getTotalprice() == null || entity.getTotalprice() <= 0) {
            validationErrors.add("Total price must be positive");
        }
        
        // Validate dates
        if (entity.getCheckin() == null) {
            validationErrors.add("Check-in date is required");
        }
        
        if (entity.getCheckout() == null) {
            validationErrors.add("Check-out date is required");
        }
        
        if (entity.getCheckin() != null && entity.getCheckout() != null) {
            if (!entity.getCheckin().isBefore(entity.getCheckout())) {
                validationErrors.add("Check-in date must be before check-out date");
            }
        }
        
        // Business rule validations
        if (entity.getTotalprice() != null && entity.getTotalprice() > 10000) {
            logger.warn("High value booking detected: {}", entity.getBookingId());
        }
        
        if (!validationErrors.isEmpty()) {
            String errorMessage = String.join(", ", validationErrors);
            logger.error("Validation failed for booking {}: {}", entity.getBookingId(), errorMessage);
            throw new RuntimeException("Booking validation failed: " + errorMessage);
        } else {
            logger.info("Booking validation passed for {}", entity.getBookingId());
        }

        return entity;
    }
}
