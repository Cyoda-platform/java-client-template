package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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

@Component
public class PrepareCheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PrepareCheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PrepareCheckoutProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ShoppingCart for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ShoppingCart.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ShoppingCart entity) {
        return entity != null && entity.isValid();
    }

    private ShoppingCart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ShoppingCart> context) {
        ShoppingCart cart = context.entity();

        // Recalculate totals based on items in the cart
        double computedSubtotal = 0.0;
        if (cart.getItems() != null) {
            for (ShoppingCart.Item it : cart.getItems()) {
                if (it != null && it.getPriceAtAdd() != null && it.getQuantity() != null) {
                    computedSubtotal += it.getPriceAtAdd() * it.getQuantity();
                }
            }
        }

        // Update cart modified timestamp
        cart.setModifiedAt(Instant.now().toString());

        // Build Order entity from ShoppingCart
        Order order = new Order();
        // Use cartId as business orderId to link Order -> Cart (acceptable for sample system)
        order.setOrderId(cart.getCartId());
        order.setCreatedAt(Instant.now().toString());
        order.setCustomerUserId(cart.getCustomerUserId());
        order.setStatus("Created");

        // Map items
        List<Order.Item> orderItems = new ArrayList<>();
        if (cart.getItems() != null) {
            for (ShoppingCart.Item ci : cart.getItems()) {
                if (ci == null) continue;
                Order.Item oi = new Order.Item();
                oi.setProductSku(ci.getProductSku());
                oi.setQuantity(ci.getQuantity());
                // Use priceAtAdd as the historical unit price for the order
                oi.setUnitPrice(ci.getPriceAtAdd());
                orderItems.add(oi);
            }
        }
        order.setItems(orderItems);

        // Set totals
        order.setSubtotal(computedSubtotal);
        order.setTotal(computedSubtotal); // no taxes/shipping in this simple flow

        // Validate order before creating
        if (!order.isValid()) {
            logger.error("Generated order from cart {} is invalid, aborting order creation", cart.getCartId());
            // Do not throw; simply return cart. The workflow's criterion/next steps will handle invalid state.
            return cart;
        }

        // Persist Order entity as a separate entity (do not update the triggering ShoppingCart via entityService)
        try {
            entityService.addItem(
                Order.ENTITY_NAME,
                String.valueOf(Order.ENTITY_VERSION),
                order
            ).exceptionally(ex -> {
                logger.error("Failed to create Order entity for cart {}: {}", cart.getCartId(), ex.getMessage(), ex);
                return null;
            });
        } catch (Exception ex) {
            logger.error("Unexpected error while creating Order entity for cart {}: {}", cart.getCartId(), ex.getMessage(), ex);
        }

        return cart;
    }
}