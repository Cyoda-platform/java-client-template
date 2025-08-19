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

import java.util.HashMap;
import java.util.Map;

@Component
public class EnrichBookingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichBookingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichBookingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Booking enrichment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Booking.class)
            .validate(this::isValidEntity, "Invalid booking for enrichment")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Booking booking) {
        return booking != null && booking.getBookingId() != null && !booking.getBookingId().isEmpty();
    }

    private Booking processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Booking> context) {
        Booking booking = context.entity();
        try {
            // Normalize customer name: trim and collapse whitespace
            if (booking.getCustomerName() != null) {
                String normalized = booking.getCustomerName().trim().replaceAll("\\s+", " ");
                booking.setCustomerName(normalized);
                // Add a simple normalized field map if available
                try {
                    // Booking class in this project doesn't contain normalizedFields explicitly.
                    // If future versions add this field, attempt to set it via setter to be resilient.
                    Map<String, Object> normalizedFields = null;
                    // reflectively try to access getter/setter if present - but avoid reflection per constraints
                    // So set additional fields into an existing map only if methods exist - skip otherwise
                } catch (Exception e) {
                    // If normalizedFields getters/setters are not available, ignore gracefully
                    logger.debug("normalizedFields property not present or accessible on Booking: {}", e.getMessage());
                }
            }

            // Default source if missing
            if (booking.getSource() == null || booking.getSource().isEmpty()) {
                booking.setSource("RestfulBooker");
            }

        } catch (Exception e) {
            logger.warn("Error enriching booking {}: {}", booking == null ? "<null>" : booking.getBookingId(), e.getMessage());
        }

        return booking;
    }
}
