package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cartorder.version_1.CartOrder;
import com.java_template.application.entity.cartorder.version_1.CartOrder.Item;
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
public class CalculateTotalsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CalculateTotalsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public CalculateTotalsProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CartOrder totals for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CartOrder.class)
            .validate(this::isValidEntity, "Invalid cart order state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CartOrder entity) {
        return entity != null && entity.isValid();
    }

    private CartOrder processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CartOrder> context) {
        CartOrder order = context.entity();
        try {
            double subtotal = 0.0;
            for (Item item : order.getItems()) {
                if (item.getQuantity() == null || item.getUnitPrice() == null) continue;
                subtotal += item.getQuantity() * item.getUnitPrice();
            }
            double tax = computeTax(subtotal, order.getCustomerId(), order.getStatus());
            double total = subtotal + tax;
            order.setSubtotal(subtotal);
            order.setTax(tax);
            order.setTotal(total);
            logger.info("Calculated totals for order {}: subtotal={}, tax={}, total={} ", order.getOrderId(), subtotal, tax, total);
            return order;
        } catch (Exception e) {
            logger.error("Error calculating totals for order {}: {}", order.getOrderId(), e.getMessage(), e);
            return order;
        }
    }

    private double computeTax(double subtotal, String customerId, String orderStatus) {
        // Simplified tax calculation: flat 10% for demonstration
        if (subtotal <= 0) return 0.0;
        return Math.round(subtotal * 0.10 * 100.0) / 100.0;
    }
}
