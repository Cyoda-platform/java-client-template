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

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class UnsubscribeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UnsubscribeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UnsubscribeProcessor(SerializerFactory serializerFactory) {
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
            logger.warn("Subscriber entity is null in UnsubscribeProcessor context");
            return null;
        }

        String currentStatus = entity.getStatus();
        if (currentStatus == null) {
            logger.warn("Subscriber status is null for email={} - skipping unsubscribe transition", entity.getEmail());
            return entity;
        }

        // Business rule: UnsubscribeProcessor is a manual transition from ACTIVE -> UNSUBSCRIBED
        if ("ACTIVE".equalsIgnoreCase(currentStatus)) {
            entity.setStatus("UNSUBSCRIBED");
            logger.info("Subscriber with email={} transitioned from ACTIVE to UNSUBSCRIBED", entity.getEmail());
        } else if ("UNSUBSCRIBED".equalsIgnoreCase(currentStatus)) {
            // already unsubscribed; no-op
            logger.info("Subscriber with email={} is already UNSUBSCRIBED; no changes applied", entity.getEmail());
        } else {
            // For any other state (e.g., PENDING_CONFIRMATION, BOUNCED), do not change status
            logger.warn("Subscriber with email={} is in state '{}' and cannot be unsubscribed by this processor. No changes applied.", entity.getEmail(), currentStatus);
        }

        return entity;
    }
}