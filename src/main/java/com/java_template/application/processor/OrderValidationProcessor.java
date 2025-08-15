package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.OrderItem;
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

import java.math.BigDecimal;

@Component
public class OrderValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OrderValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid order payload")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order order) {
        return order != null && order.getItems() != null && !order.getItems().isEmpty() && order.getCustomerId() != null;
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        try {
            // Basic totals validation
            BigDecimal subtotal = BigDecimal.ZERO;
            for (OrderItem item : order.getItems()) {
                if (item == null) continue;
                Integer qty = item.getQuantity() == null ? 0 : item.getQuantity();
                BigDecimal unit = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
                subtotal = subtotal.add(unit.multiply(BigDecimal.valueOf(qty)));
            }
            order.setSubtotal(subtotal);
            BigDecimal expectedTotal = subtotal.add(order.getTaxes() == null ? BigDecimal.ZERO : order.getTaxes()).add(order.getShipping() == null ? BigDecimal.ZERO : order.getShipping());
            order.setTotal(expectedTotal);
            logger.info("Order {} validated totals: subtotal={}, total={}", order.getId(), order.getSubtotal(), order.getTotal());
        } catch (Exception e) {
            logger.error("Error during order validation: {}", e.getMessage(), e);
        }
        return order;
    }
}
