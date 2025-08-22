package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
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

import java.time.Instant;

@Component
public class MarkSentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkSentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MarkSentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order entity = context.entity();

        if (entity == null) {
            logger.warn("MarkSentProcessor received null entity in context");
            return null;
        }

        String currentStatus = entity.getStatus();
        if (currentStatus == null) {
            logger.warn("Order {} has null status, skipping MarkSentProcessor", entity.getId());
            return entity;
        }

        // Business rule:
        // - If Order is in PICKING -> automatically transition to SENT
        // - Otherwise leave unchanged (idempotent)
        if ("PICKING".equalsIgnoreCase(currentStatus)) {
            logger.info("Order {} status '{}' -> 'SENT'", entity.getId(), currentStatus);
            entity.setStatus("SENT");
            entity.setUpdatedAt(Instant.now().toString());
        } else {
            logger.info("Order {} not in PICKING state (current='{}'), no transition performed", entity.getId(), currentStatus);
        }

        return entity;
    }
}