package com.java_template.application.processor;

import com.java_template.application.entity.deliveryperson.version_1.DeliveryPerson;
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
 * DeliveryPersonCompletionProcessor - Handles delivery person completion workflow transition
 * Transition: BUSY → ACTIVE
 */
@Component
public class DeliveryPersonCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryPersonCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DeliveryPersonCompletionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing delivery person completion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DeliveryPerson.class)
                .validate(this::isValidEntityWithMetadata, "Invalid delivery person entity wrapper")
                .map(this::processDeliveryPersonCompletion)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<DeliveryPerson> entityWithMetadata) {
        DeliveryPerson entity = entityWithMetadata.entity();
        return entity != null && entity.isValid();
    }

    private EntityWithMetadata<DeliveryPerson> processDeliveryPersonCompletion(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DeliveryPerson> context) {

        EntityWithMetadata<DeliveryPerson> entityWithMetadata = context.entityResponse();
        DeliveryPerson deliveryPerson = entityWithMetadata.entity();

        logger.debug("Processing delivery person completion: {}", deliveryPerson.getDeliveryPersonId());

        // Mark as available again
        deliveryPerson.setIsAvailable(true);
        deliveryPerson.setUpdatedAt(LocalDateTime.now());
        
        // Increment total deliveries
        if (deliveryPerson.getTotalDeliveries() != null) {
            deliveryPerson.setTotalDeliveries(deliveryPerson.getTotalDeliveries() + 1);
        } else {
            deliveryPerson.setTotalDeliveries(1);
        }

        logger.info("Delivery person completed delivery: {}", deliveryPerson.getName());
        
        return entityWithMetadata;
    }
}
