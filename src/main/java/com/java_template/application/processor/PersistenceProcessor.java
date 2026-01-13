package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PersistenceProcessor - Persists order state changes to storage
 * 
 * <p>Handles persistence of order entities after validation, routing, and execution.
 * Ensures order state is durably stored in the system.
 */
@Component
public class PersistenceProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    /**
     * Constructs PersistenceProcessor with required dependencies.
     *
     * @param serializerFactory Factory for creating serializers
     * @param entityService Service for entity operations
     */
    public PersistenceProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Persisting Order for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .withErrorHandler(this::handlePersistenceError)
                .map(this::persistOrder)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Persists the order entity.
     *
     * @param context Processing context with order entity
     * @return EntityWithMetadata with persisted order
     */
    private EntityWithMetadata<Order> persistOrder(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Persisting order: {}", order.getOrderId());

        try {
            // The entity is automatically persisted via the return value
            // No explicit save needed - framework handles persistence
            logger.info("Order {} persisted successfully", order.getOrderId());
            return entityWithMetadata;
        } catch (Exception e) {
            logger.error("Error persisting order: {}", order.getOrderId(), e);
            throw new RuntimeException("Persistence error for order " + order.getOrderId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Custom error handler for persistence errors.
     *
     * @param error The error that occurred
     * @param entityWithMetadata The entity being processed
     * @return ErrorInfo with appropriate code and message
     */
    private ErrorInfo handlePersistenceError(Throwable error, EntityWithMetadata<Order> entityWithMetadata) {
        String orderId = entityWithMetadata != null && entityWithMetadata.entity() != null 
                ? entityWithMetadata.entity().getOrderId() 
                : "UNKNOWN";
        return new ErrorInfo("PERSISTENCE_ERROR", 
                "Failed to persist order " + orderId + ": " + error.getMessage());
    }
}

