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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Processor to notify customer when order is delivered
 * 
 * This processor is triggered during the shipped -> delivered transition.
 * It simulates sending a delivery confirmation notification to the customer.
 */
@Component
public class notifyOrderDelivered implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(notifyOrderDelivered.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public notifyOrderDelivered(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processDeliveryNotification)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid() && technicalId != null;
    }

    /**
     * Main business logic for sending delivery notification
     */
    private EntityWithMetadata<Order> processDeliveryNotification(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.info("Sending delivery notification for order: {}", order.getOrderId());

        // Simulate sending delivery confirmation email to customer
        String notificationId = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8);
        
        logger.info("Delivery notification sent for order {} to customer {} with notification ID: {}", 
                   order.getOrderId(), order.getCustomerEmail(), notificationId);

        // Update timestamps
        order.setUpdatedAt(LocalDateTime.now());

        return entityWithMetadata;
    }
}

