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

import java.time.Instant;

@Component
public class StoreBookingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StoreBookingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StoreBookingProcessor(SerializerFactory serializerFactory) {
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

        // Business logic:
        // If entity is valid (validated earlier by ValidateBookingProcessor), mark it as persisted by setting persistedAt timestamp.
        // If somehow entity is invalid at this stage, log and leave as-is (it will not be marked as persisted).
        if (entity == null) {
            logger.warn("Received null Booking entity in processing context");
            return null;
        }

        if (!entity.isValid()) {
            logger.warn("Booking (id={}) is not valid and will not be marked as persisted", entity.getBookingId());
            return entity;
        }

        String now = Instant.now().toString();
        entity.setPersistedAt(now);
        logger.info("Booking (id={}) marked as persisted at {}", entity.getBookingId(), now);

        return entity;
    }
}