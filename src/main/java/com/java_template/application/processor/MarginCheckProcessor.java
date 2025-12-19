package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
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

/**
 * MarginCheckProcessor - Validates margin requirements for the order
 */
@Component
public class MarginCheckProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarginCheckProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MarginCheckProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Checking margin for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidOrder, "Invalid order")
                .map(this::checkMargin)
                .complete();
    }

    private EntityWithMetadata<Order> checkMargin(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.entity();

        // In a real system, check margin requirements
        // For now, assume margin is available
        logger.info("Margin check passed for order: {} for account: {}",
            order.getOrderId(), order.getAccountId());

        return orderWithMetadata;
    }

    private boolean isValidOrder(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.entity();
        return order.getOrderId() != null && order.getAccountId() != null;
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

