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
 * AcknowledgeProcessor - Acknowledges routed orders
 * 
 * <p>Marks orders as acknowledged after successful routing.
 * Records acknowledgment timestamp and prepares order for execution.
 */
@Component
public class AcknowledgeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AcknowledgeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    /**
     * Constructs AcknowledgeProcessor with required dependencies.
     *
     * @param serializerFactory Factory for creating serializers
     * @param entityService Service for entity operations
     */
    public AcknowledgeProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Acknowledging Order for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .withErrorHandler(this::handleAcknowledgeError)
                .map(this::acknowledgeOrder)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Acknowledges the order and records acknowledgment timestamp.
     *
     * @param context Processing context with order entity
     * @return EntityWithMetadata with acknowledged order
     */
    private EntityWithMetadata<Order> acknowledgeOrder(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Acknowledging order: {}", order.getOrderId());

        try {
            // Record acknowledgment timestamp
            order.setUpdatedAt(LocalDateTime.now());
            
            logger.info("Order {} acknowledged successfully", order.getOrderId());
            return entityWithMetadata;
        } catch (Exception e) {
            logger.error("Error acknowledging order: {}", order.getOrderId(), e);
            throw new RuntimeException("Acknowledgment error for order " + order.getOrderId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Custom error handler for acknowledgment errors.
     *
     * @param error The error that occurred
     * @param entityWithMetadata The entity being processed
     * @return ErrorInfo with appropriate code and message
     */
    private ErrorInfo handleAcknowledgeError(Throwable error, EntityWithMetadata<Order> entityWithMetadata) {
        String orderId = entityWithMetadata != null && entityWithMetadata.entity() != null 
                ? entityWithMetadata.entity().getOrderId() 
                : "UNKNOWN";
        return new ErrorInfo("ACKNOWLEDGE_ERROR", 
                "Failed to acknowledge order " + orderId + ": " + error.getMessage());
    }
}

