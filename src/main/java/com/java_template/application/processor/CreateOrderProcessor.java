package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CreateOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateOrderProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CreateOrder for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ShoppingCart.class)
            .validate(this::isValidEntity, "Invalid cart state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ShoppingCart cart) {
        return cart != null && cart.isValid();
    }

    private ProcessorSerializer.ProcessorEntityExecutionContext<ShoppingCart> processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ShoppingCart> context) {
        ShoppingCart cart = context.entity();
        try {
            Order order = new Order();
            order.setCustomerId(cart.getCustomerId());
            order.setStatus("PENDING");
            order.setCreatedAt(Instant.now().toString());
            order.setUpdatedAt(order.getCreatedAt());

            List<Order.OrderItem> items = new ArrayList<>();
            BigDecimal subtotal = BigDecimal.ZERO;
            for (ShoppingCart.CartItem it : cart.getItems()) {
                Order.OrderItem oi = new Order.OrderItem();
                oi.setProductId(it.getProductId());
                oi.setQuantity(it.getQuantity());
                oi.setUnitPrice(it.getPriceAtAdd());
                items.add(oi);
                subtotal = subtotal.add(it.getPriceAtAdd().multiply(new BigDecimal(it.getQuantity())));
            }
            order.setItems(items);
            order.setSubtotal(subtotal);
            // naive tax and shipping calculations for prototype
            BigDecimal tax = subtotal.multiply(new BigDecimal("0.075"));
            BigDecimal shipping = new BigDecimal("5.00");
            BigDecimal total = subtotal.add(tax).add(shipping);
            order.setTax(tax);
            order.setShipping(shipping);
            order.setTotal(total);

            // persist order
            CompletableFuture<UUID> fut = entityService.addItem(
                Order.ENTITY_NAME,
                String.valueOf(Order.ENTITY_VERSION),
                order
            );
            fut.whenComplete((id, ex) -> {
                if (ex != null) {
                    logger.error("Failed to persist order", ex);
                } else {
                    logger.info("Order created with technicalId {}", id);
                    // update cart status to CHECKED_OUT? The payment flow will adjust further; for now set to CHECKOUT_IN_PROGRESS
                    try {
                        if (context.attributes() != null && context.attributes().get("technicalId") != null) {
                            String tid = (String) context.attributes().get("technicalId");
                            com.fasterxml.jackson.databind.node.ObjectNode cartNode = com.java_template.common.util.Json.mapper().convertValue(cart, com.fasterxml.jackson.databind.node.ObjectNode.class);
                            cartNode.put("status", "CHECKOUT_IN_PROGRESS");
                            entityService.updateItem(ShoppingCart.ENTITY_NAME, String.valueOf(ShoppingCart.ENTITY_VERSION), UUID.fromString(tid), cartNode).whenComplete((u, ex2) -> {
                                if (ex2 != null) logger.error("Failed to update cart status after order creation", ex2);
                            });
                        }
                    } catch (Exception ee) {
                        logger.warn("Failed to set cart status after order creation", ee);
                    }
                }
            });

        } catch (Exception ex) {
            logger.error("Error creating order from cart", ex);
        }
        return context;
    }
}
