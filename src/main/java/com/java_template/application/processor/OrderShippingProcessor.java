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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * OrderShippingProcessor - Process order shipping
 * 
 * Transition: ship_order (approved → shipped)
 * Purpose: Process order shipping
 */
@Component
public class OrderShippingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderShippingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OrderShippingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order shipping for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Order> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing order shipping: {}", order.getOrderId());

        // 1. Set shipDate = current timestamp
        order.setShipDate(LocalDateTime.now());

        // 2. Set updatedAt = current timestamp
        order.setUpdatedAt(LocalDateTime.now());

        // 3. Log shipping information with order ID and shipping address
        logger.info("Order {} shipped to address: {}, {}, {} {}, {} at {}", 
                order.getOrderId(),
                order.getShippingAddress() != null ? order.getShippingAddress().getStreet() : "N/A",
                order.getShippingAddress() != null ? order.getShippingAddress().getCity() : "N/A",
                order.getShippingAddress() != null ? order.getShippingAddress().getState() : "N/A",
                order.getShippingAddress() != null ? order.getShippingAddress().getZipCode() : "N/A",
                order.getShippingAddress() != null ? order.getShippingAddress().getCountry() : "N/A",
                LocalDateTime.now());

        return entityWithMetadata;
    }
}
