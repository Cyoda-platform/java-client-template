package com.java_template.application.processor;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
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
        if (entity == null) {
            logger.warn("Subscriber entity is null in execution context");
            return null;
        }

        String currentStatus = entity.getStatus();
        if (currentStatus != null && currentStatus.equalsIgnoreCase("UNSUBSCRIBED")) {
            logger.info("Subscriber {} is already unsubscribed.", entity.getSubscriberId());
            return entity;
        }

        // Deactivation business logic:
        // - Only change the subscriber status to UNSUBSCRIBED.
        // - Preserve other fields as-is. Do not perform persistence here; Cyoda will persist the modified entity.
        entity.setStatus("UNSUBSCRIBED");
        logger.info("Subscriber {} status changed to UNSUBSCRIBED.", entity.getSubscriberId());

        return entity;
    }
}