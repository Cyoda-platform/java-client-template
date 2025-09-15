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
 * DeliveryPersonSuspensionProcessor - Handles delivery person suspension workflow transition
 * Transition: ACTIVE → SUSPENDED
 */
@Component
public class DeliveryPersonSuspensionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryPersonSuspensionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DeliveryPersonSuspensionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing delivery person suspension for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DeliveryPerson.class)
                .validate(this::isValidEntityWithMetadata, "Invalid delivery person entity wrapper")
                .map(this::processDeliveryPersonSuspension)
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

    private EntityWithMetadata<DeliveryPerson> processDeliveryPersonSuspension(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DeliveryPerson> context) {

        EntityWithMetadata<DeliveryPerson> entityWithMetadata = context.entityResponse();
        DeliveryPerson deliveryPerson = entityWithMetadata.entity();

        logger.debug("Processing delivery person suspension: {}", deliveryPerson.getDeliveryPersonId());

        // Set offline and unavailable
        deliveryPerson.setIsOnline(false);
        deliveryPerson.setIsAvailable(false);
        deliveryPerson.setUpdatedAt(LocalDateTime.now());

        logger.info("Delivery person suspended: {}", deliveryPerson.getName());
        
        return entityWithMetadata;
    }
}
