package com.java_template.application.processor;

import com.java_template.application.entity.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public OrderProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("OrderProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(Order::isValid)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "OrderProcessor".equals(modelSpec.operationName()) &&
               "order".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Order processEntityLogic(Order order) {
        try {
            logger.info("Processing Order with orderId: {}", order.getOrderId());
            if (order.getCustomerId() == null || order.getCustomerId().isBlank()) {
                logger.error("Order customerId is invalid");
                return order;
            }
            if (order.getItems() == null || order.getItems().isEmpty()) {
                logger.error("Order items are empty");
                return order;
            }
            boolean paymentSuccess = true;
            if (paymentSuccess) {
                order.setStatus("PAID");
                logger.info("Payment successful for orderId: {}", order.getOrderId());
                order.setStatus("SHIPPED");
                logger.info("Shipment initiated for orderId: {}", order.getOrderId());
            } else {
                order.setStatus("FAILED");
                logger.error("Payment failed for orderId: {}", order.getOrderId());
            }
        } catch (Exception e) {
            logger.error("Error processing order", e);
        }
        return order;
    }
}
