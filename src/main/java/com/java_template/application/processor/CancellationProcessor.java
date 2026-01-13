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

import java.time.LocalDateTime;

/**
 * CancellationProcessor - Processes order cancellations
 * 
 * <p>Handles cancellation of orders at any stage of the lifecycle.
 * Records cancellation timestamp and reason.
 */
@Component
public class CancellationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CancellationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    /**
     * Constructs CancellationProcessor with required dependencies.
     *
     * @param serializerFactory Factory for creating serializers
     * @param entityService Service for entity operations
     */
    public CancellationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing cancellation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .withErrorHandler(this::handleCancellationError)
                .map(this::cancelOrder)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Cancels the order and records cancellation timestamp.
     *
     * @param context Processing context with order entity
     * @return EntityWithMetadata with cancelled order
     */
    private EntityWithMetadata<Order> cancelOrder(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Cancelling order: {}", order.getOrderId());

        try {
            // Record cancellation timestamp
            order.setUpdatedAt(LocalDateTime.now());
            
            logger.info("Order {} cancelled successfully", order.getOrderId());
            return entityWithMetadata;
        } catch (Exception e) {
            logger.error("Error cancelling order: {}", order.getOrderId(), e);
            throw new RuntimeException("Cancellation error for order " + order.getOrderId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Custom error handler for cancellation errors.
     *
     * @param error The error that occurred
     * @param entityWithMetadata The entity being processed
     * @return ErrorInfo with appropriate code and message
     */
    private ErrorInfo handleCancellationError(Throwable error, EntityWithMetadata<Order> entityWithMetadata) {
        String orderId = entityWithMetadata != null && entityWithMetadata.entity() != null 
                ? entityWithMetadata.entity().getOrderId() 
                : "UNKNOWN";
        return new ErrorInfo("CANCELLATION_ERROR", 
                "Failed to cancel order " + orderId + ": " + error.getMessage());
    }
}

