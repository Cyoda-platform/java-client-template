package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class CreateOrderFromCartProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromCartProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateOrderFromCartProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CreateOrderFromCart for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart for order creation")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && cart.getId() != null && cart.getStatus() != null && "CHECKED_OUT".equals(cart.getStatus());
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Build an Order entity from the cart snapshot
        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setCustomerId(cart.getCustomerId());
        order.setCurrency(cart.getCurrency());
        order.setCreatedAt(Instant.now().toString());
        order.setUpdatedAt(Instant.now().toString());
        order.setStatus("PENDING_PAYMENT");
        order.setPaymentStatus("NOT_ATTEMPTED");

        List<OrderItem> items = new ArrayList<>();
        if (cart.getItems() != null) {
            cart.getItems().forEach(ci -> {
                OrderItem oi = new OrderItem();
                oi.setProductId(ci.getProductId());
                oi.setQuantity(ci.getQuantity());
                oi.setUnitPrice(ci.getUnitPrice());
                items.add(oi);
            });
        }
        order.setItems(items);
        order.setTotalAmount(cart.getTotalAmount());

        // Attach generated order id to cart for traceability
        cart.setLinkedOrderId(order.getId());
        cart.setUpdatedAt(Instant.now().toString());

        logger.info("Created Order {} from Cart {} with {} items", order.getId(), cart.getId(), items.size());

        // For the purposes of Cyoda processors, return the modified cart. The orchestrator will persist both
        // cart and created order in downstream components in a real system. We attach the order as metadata on the cart
        // by using cart.setLinkedOrder.
        cart.setLinkedOrder(order);
        return cart;
    }
}
