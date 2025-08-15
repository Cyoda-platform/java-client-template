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

@Component
public class OrderFulfillmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderFulfillmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer(serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    private final ProcessorSerializer serializer;

    public OrderFulfillmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order fulfillment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid order for fulfillment")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order order) {
        return order != null && order.getFulfillmentStatus() != null;
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        try {
            order.setFulfillmentStatus("Shipped");
            logger.info("Order {} marked as shipped", order.getId());
        } catch (Exception e) {
            logger.error("Error during order fulfillment: {}", e.getMessage(), e);
        }
        return order;
    }
}
