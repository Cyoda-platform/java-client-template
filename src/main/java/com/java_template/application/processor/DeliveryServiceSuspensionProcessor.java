package com.java_template.application.processor;

import com.java_template.application.entity.deliveryservice.version_1.DeliveryService;
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
 * DeliveryServiceSuspensionProcessor - Handles delivery service suspension workflow transition
 * Transition: ACTIVE → SUSPENDED
 */
@Component
public class DeliveryServiceSuspensionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryServiceSuspensionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DeliveryServiceSuspensionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing delivery service suspension for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DeliveryService.class)
                .validate(this::isValidEntityWithMetadata, "Invalid delivery service entity wrapper")
                .map(this::processDeliveryServiceSuspension)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<DeliveryService> entityWithMetadata) {
        DeliveryService entity = entityWithMetadata.entity();
        return entity != null && entity.isValid();
    }

    private EntityWithMetadata<DeliveryService> processDeliveryServiceSuspension(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DeliveryService> context) {

        EntityWithMetadata<DeliveryService> entityWithMetadata = context.entityResponse();
        DeliveryService deliveryService = entityWithMetadata.entity();

        logger.debug("Processing delivery service suspension: {}", deliveryService.getDeliveryServiceId());

        // Suspend delivery service
        deliveryService.setIsActive(false);
        deliveryService.setUpdatedAt(LocalDateTime.now());

        // Note: In a real implementation, we would:
        // - Notify all restaurants using this service
        // - Reassign pending deliveries to other services
        // - Suspend all delivery persons for this service
        
        logger.info("Delivery service suspended: {}", deliveryService.getName());
        
        return entityWithMetadata;
    }
}
