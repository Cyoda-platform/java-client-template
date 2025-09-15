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
 * DeliveryPersonAssignmentProcessor - Handles delivery person assignment workflow transition
 * Transition: ACTIVE → BUSY
 */
@Component
public class DeliveryPersonAssignmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryPersonAssignmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DeliveryPersonAssignmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing delivery person assignment for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DeliveryPerson.class)
                .validate(this::isValidEntityWithMetadata, "Invalid delivery person entity wrapper")
                .map(this::processDeliveryPersonAssignment)
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

    private EntityWithMetadata<DeliveryPerson> processDeliveryPersonAssignment(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DeliveryPerson> context) {

        EntityWithMetadata<DeliveryPerson> entityWithMetadata = context.entityResponse();
        DeliveryPerson deliveryPerson = entityWithMetadata.entity();

        logger.debug("Processing delivery person assignment: {}", deliveryPerson.getDeliveryPersonId());

        // Mark as unavailable (busy with delivery)
        deliveryPerson.setIsAvailable(false);
        deliveryPerson.setUpdatedAt(LocalDateTime.now());

        logger.info("Delivery person assigned to delivery: {}", deliveryPerson.getName());
        
        return entityWithMetadata;
    }
}
