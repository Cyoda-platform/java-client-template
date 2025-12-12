package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * PersistOrder Processor
 * 
 * Persists order to in-memory database (Cyoda).
 * Updates order metadata with persistence information.
 */
@Component
public class PersistOrder implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistOrder.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PersistOrder(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistOrder for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::persistOrderLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Order> persistOrderLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.info("Persisting order: {} to database", order.getOrderId());

        // Update timestamps
        order.setUpdatedAt(LocalDateTime.now());

        // Store persistence information in metadata
        if (order.getMetadata() == null) {
            order.setMetadata(new HashMap<>());
        }
        
        Map<String, Object> persistenceInfo = new HashMap<>();
        persistenceInfo.put("persistedAt", System.currentTimeMillis());
        persistenceInfo.put("persistenceStatus", "PERSISTED");
        persistenceInfo.put("databaseId", entityWithMetadata.metadata().getId().toString());
        
        order.getMetadata().put("persistence", persistenceInfo);

        logger.info("Order {} persisted successfully with ID: {}", 
                order.getOrderId(), entityWithMetadata.metadata().getId());
        
        return entityWithMetadata;
    }
}

