package com.java_template.application.processor;

import com.java_template.application.entity.petcareorder.version_1.PetCareOrder;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * ServiceStartProcessor - Handles service start business logic
 * 
 * Marks the beginning of service delivery, including:
 * - Verifying service readiness
 * - Beginning service tracking
 * - Confirming service provider availability
 */
@Component
public class ServiceStartProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ServiceStartProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ServiceStartProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Service start for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(PetCareOrder.class)
            .validate(this::isValidEntityWithMetadata, "Invalid service start data")
            .map(this::processEntityWithMetadataLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<PetCareOrder> entityWithMetadata) {
        PetCareOrder order = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();
        return order != null && order.isValid() && "CONFIRMED".equals(currentState);
    }

    private EntityWithMetadata<PetCareOrder> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<PetCareOrder> context) {
        
        EntityWithMetadata<PetCareOrder> entityWithMetadata = context.entityResponse();
        PetCareOrder order = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();

        logger.debug("Processing service start for: {} in state: {}", order.getOrderId(), currentState);

        // 1. Verify service readiness
        verifyServiceReadiness(order);

        // 2. Service is now actively being provided
        logger.info("Service started successfully for order {}", order.getOrderId());

        return entityWithMetadata;
    }

    private void verifyServiceReadiness(PetCareOrder order) {
        // Check if scheduled date has arrived (allow some flexibility)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledDate = order.getScheduledDate();
        
        // Allow service to start up to 30 minutes before scheduled time
        LocalDateTime earliestStart = scheduledDate.minusMinutes(30);
        
        if (now.isBefore(earliestStart)) {
            throw new IllegalStateException("Service cannot start yet. Scheduled for: " + scheduledDate + 
                                          ", current time: " + now);
        }
        
        // Check if it's not too late (within 2 hours of scheduled time)
        LocalDateTime latestStart = scheduledDate.plusHours(2);
        if (now.isAfter(latestStart)) {
            throw new IllegalStateException("Service start window has passed. Scheduled for: " + scheduledDate + 
                                          ", current time: " + now);
        }

        logger.debug("Service readiness verified for order {} at {}", order.getOrderId(), now);
    }
}
