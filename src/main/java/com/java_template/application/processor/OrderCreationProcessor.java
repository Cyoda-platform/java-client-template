package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
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

import java.util.UUID;

@Component
public class OrderCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OrderCreationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing order creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ShoppingCart.class)
            .validate(this::isValidEntity, "Invalid cart for order creation")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ShoppingCart cart) {
        return cart != null && cart.getId() != null;
    }

    private ShoppingCart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ShoppingCart> context) {
        ShoppingCart cart = context.entity();
        try {
            Order order = new Order();
            order.setId(UUID.randomUUID());
            order.setUserId(cart.getUserId());
            order.setCurrency(cart.getCurrency());
            order.setSubtotal(cart.getSubtotal());
            order.setTotal(cart.getTotal());
            order.setPaymentStatus("UNPAID");
            order.setStatus("PENDING");
            // In real implementation persist order and attach order id back. Here we log.
            logger.info("Created order {} from cart {}", order.getId(), cart.getId());
            // There's no field on ShoppingCart to attach order id; returning cart unchanged.
        } catch (Exception e) {
            logger.error("Error during order creation for cart {}: {}", cart != null ? cart.getId() : "<null>", e.getMessage());
        }
        return cart;
    }
}
