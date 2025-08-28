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
public class ActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ActivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
            .withErrorHandler((error, entity) -> {
                    // Log the extraction error and let the serializer handle error propagation.
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return null;
                })
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

        // Activation business logic:
        // - If subscriber is already active, no-op.
        // - Otherwise set active = true. Do not modify lastNotifiedAt here (notifications happen in NotificationProcessor).
        // - Ensure channels/filters remain intact (entity.isValid() was checked earlier).
        try {
            if (entity.getActive() != null && Boolean.TRUE.equals(entity.getActive())) {
                logger.info("Subscriber {} is already active.", entity.getSubscriberId());
                return entity;
            }
            entity.setActive(Boolean.TRUE);
            logger.info("Subscriber {} activated.", entity.getSubscriberId());
        } catch (Exception ex) {
            logger.error("Error while activating subscriber {}: {}", entity != null ? entity.getSubscriberId() : "unknown", ex.getMessage(), ex);
            throw ex;
        }

        return entity;
    }
}