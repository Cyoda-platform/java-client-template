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
        
        // Business logic:
        // - If the subscriber is currently active, mark as unsubscribed by setting active = false.
        // - If already inactive, do nothing (idempotent).
        // - Do not perform external entity add/update/delete operations on this entity; changing the entity state
        //   is sufficient -- Cyoda will persist the updated entity automatically.
        
        if (entity == null) {
            logger.warn("Received null Subscriber entity in UnsubscribeProcessor");
            return entity;
        }

        try {
            if (Boolean.FALSE.equals(entity.getActive())) {
                logger.info("Subscriber {} is already inactive; no action taken.", entity.getId());
                return entity;
            }

            // Mark subscriber as unsubscribed
            entity.setActive(false);
            logger.info("Subscriber {} has been marked as unsubscribed.", entity.getId());
        } catch (Exception ex) {
            logger.error("Error while unsubscribing subscriber {}: {}", entity.getId(), ex.getMessage(), ex);
            // In case of unexpected error, we leave the entity state as-is; the serializer will handle error propagation.
        }
        
        return entity;
    }
}