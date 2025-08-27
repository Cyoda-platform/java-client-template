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
public class DeactivateSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeactivateSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DeactivateSubscriberProcessor(SerializerFactory serializerFactory) {
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

        // Business logic: deactivate the subscriber.
        // Rules:
        // - Use only existing getters/setters.
        // - Do not call entityService update on this entity; Cyoda will persist changes automatically.
        // - If subscriber is already inactive, keep state but log the event.
        if (entity == null) {
            logger.warn("No subscriber entity present in execution context.");
            return entity;
        }

        Boolean currentlyActive = entity.getActive();
        if (currentlyActive == null) {
            // Treat null as not active for safety, but set explicit false to mark deactivated
            entity.setActive(Boolean.FALSE);
            logger.info("Subscriber {} had null active flag. Set to false (deactivated).", entity.getSubscriberId());
        } else if (Boolean.FALSE.equals(currentlyActive)) {
            // Already inactive
            logger.info("Subscriber {} is already inactive.", entity.getSubscriberId());
            // No further state changes
        } else {
            // Active -> deactivate
            entity.setActive(Boolean.FALSE);
            logger.info("Subscriber {} deactivated.", entity.getSubscriberId());
        }

        return entity;
    }
}