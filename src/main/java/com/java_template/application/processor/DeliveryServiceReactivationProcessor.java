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
 * DeliveryServiceReactivationProcessor - Handles delivery service reactivation workflow transition
 * Transition: SUSPENDED → ACTIVE
 */
@Component
public class DeliveryServiceReactivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryServiceReactivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DeliveryServiceReactivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing delivery service reactivation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DeliveryService.class)
                .validate(this::isValidEntityWithMetadata, "Invalid delivery service entity wrapper")
                .map(this::processDeliveryServiceReactivation)
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

    private EntityWithMetadata<DeliveryService> processDeliveryServiceReactivation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DeliveryService> context) {

        EntityWithMetadata<DeliveryService> entityWithMetadata = context.entityResponse();
        DeliveryService deliveryService = entityWithMetadata.entity();

        logger.debug("Processing delivery service reactivation: {}", deliveryService.getDeliveryServiceId());

        // Reactivate delivery service
        deliveryService.setIsActive(true);
        deliveryService.setUpdatedAt(LocalDateTime.now());

        logger.info("Delivery service reactivated: {}", deliveryService.getName());
        
        return entityWithMetadata;
    }
}
