package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
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
public class DeliveryConfirmedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryConfirmedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public DeliveryConfirmedProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DeliveryConfirmed for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid order for delivery confirmation")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order order) {
        return order != null && order.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        order.setFulfillmentStatus("DELIVERED");
        logger.info("Order {} marked as DELIVERED", order.getOrderId());
        return order;
    }
}
