package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ErrorInfo;
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
public class SuspendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SuspendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SuspendProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
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

        // Business logic for suspending a Subscriber:
        // - Only toggle active flag to false to represent SUSPENDED state.
        // - Do not perform external updates on the triggering entity; Cyoda will persist changes automatically.
        if (entity == null) {
            logger.warn("Received null Subscriber entity in execution context");
            return null;
        }

        try {
            Boolean active = entity.getActive();
            if (Boolean.TRUE.equals(active)) {
                entity.setActive(Boolean.FALSE);
                logger.info("Subscriber {} suspended (active=false)", entity.getSubscriberId());
            } else {
                // If already suspended or active flag is false, log and leave as-is.
                logger.warn("Subscriber {} is already suspended or inactive (active={})", entity.getSubscriberId(), active);
            }
        } catch (Exception ex) {
            logger.error("Error while processing suspend for subscriber {}: {}", entity != null ? entity.getSubscriberId() : "unknown", ex.getMessage(), ex);
        }

        return entity;
    }
}