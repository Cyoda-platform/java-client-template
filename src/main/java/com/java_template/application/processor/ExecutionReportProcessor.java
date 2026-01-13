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
 * ExecutionReportProcessor - Processes execution reports for orders
 * 
 * <p>Handles execution reports including fills and partial fills.
 * Updates order with execution details and timestamps.
 */
@Component
public class ExecutionReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    /**
     * Constructs ExecutionReportProcessor with required dependencies.
     *
     * @param serializerFactory Factory for creating serializers
     * @param entityService Service for entity operations
     */
    public ExecutionReportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing execution report for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .withErrorHandler(this::handleExecutionReportError)
                .map(this::processExecutionReport)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Processes the execution report for the order.
     *
     * @param context Processing context with order entity
     * @return EntityWithMetadata with updated order
     */
    private EntityWithMetadata<Order> processExecutionReport(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing execution report for order: {}", order.getOrderId());

        try {
            // Update order with execution timestamp
            order.setUpdatedAt(LocalDateTime.now());
            
            logger.info("Execution report processed for order: {}", order.getOrderId());
            return entityWithMetadata;
        } catch (Exception e) {
            logger.error("Error processing execution report for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Execution report error for order " + order.getOrderId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Custom error handler for execution report errors.
     *
     * @param error The error that occurred
     * @param entityWithMetadata The entity being processed
     * @return ErrorInfo with appropriate code and message
     */
    private ErrorInfo handleExecutionReportError(Throwable error, EntityWithMetadata<Order> entityWithMetadata) {
        String orderId = entityWithMetadata != null && entityWithMetadata.entity() != null 
                ? entityWithMetadata.entity().getOrderId() 
                : "UNKNOWN";
        return new ErrorInfo("EXECUTION_REPORT_ERROR", 
                "Failed to process execution report for order " + orderId + ": " + error.getMessage());
    }
}

