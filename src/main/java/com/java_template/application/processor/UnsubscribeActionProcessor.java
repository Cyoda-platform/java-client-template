package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

@Component
public class UnsubscribeActionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UnsubscribeActionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UnsubscribeActionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();

        if (entity == null) {
            logger.warn("Subscriber entity is null in UnsubscribeActionProcessor");
            return entity;
        }

        String currentStatus = entity.getStatus();
        if (currentStatus == null || currentStatus.isBlank()) {
            logger.warn("Subscriber [{}] has no status; cannot perform unsubscribe", entity.getId());
            return entity;
        }

        // Only allow manual unsubscribe from ACTIVE state.
        if ("ACTIVE".equalsIgnoreCase(currentStatus)) {
            entity.setStatus("UNSUBSCRIBED");
            logger.info("Subscriber [{}] moved from ACTIVE to UNSUBSCRIBED", entity.getId());
        } else if ("UNSUBSCRIBED".equalsIgnoreCase(currentStatus)) {
            // Already unsubscribed: no changes.
            logger.info("Subscriber [{}] is already UNSUBSCRIBED - no action taken", entity.getId());
        } else {
            // For other states, do not change the entity. Log for visibility.
            logger.warn("UnsubscribeActionProcessor invoked for Subscriber [{}] with non-ACTIVE status '{}'. No state change performed.",
                entity.getId(), currentStatus);
        }

        return entity;
    }
}