package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ActivateSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ActivateSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ActivateSubscriberProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Business logic: activate the subscriber.
        // Do not perform entity service updates on this entity; Cyoda will persist changes automatically.
        try {
            if (entity.getActive() != null && entity.getActive()) {
                logger.info("Subscriber {} is already active", entity.getSubscriberId());
                return entity;
            }
            entity.setActive(true);
            logger.info("Activated subscriber {}", entity.getSubscriberId());
        } catch (Exception ex) {
            logger.error("Failed to activate subscriber {}: {}", entity != null ? entity.getSubscriberId() : "unknown", ex.getMessage(), ex);
            // In case of unexpected error, leave entity unchanged; validation will handle consistency.
        }

        return entity;
    }
}