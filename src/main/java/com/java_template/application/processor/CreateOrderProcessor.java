package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
        logger.info("Processing Cart for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid Cart entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart entity) {
        return entity != null && entity.isValid();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        try {
            // Only act on carts that reached CheckedOut state
            if (cart.getStatus() == null || !"CheckedOut".equalsIgnoreCase(cart.getStatus())) {
                logger.info("Cart {} not in CheckedOut state, skipping CreateOrderProcessor", cart.getId());
                return cart;
            }

            // Basic items check
            if (cart.getItems() == null || cart.getItems().isEmpty()) {
                logger.error("Cart {} has no items - cannot create order", cart.getId());
                cart.setStatus("CheckoutFailed");
                return cart;
            }

            // Build Order
            Order order = new Order();
            order.setId(UUID.randomUUID().toString()); // business id
            order.setUserId(cart.getUserId());
            order.setCreatedAt(cart.getCreatedAt() != null ? cart.getCreatedAt() : Instant.now().toString());
            order.setStatus("Created");
            order.setPaymentStatus("Pending");
            order.setTotal(cart.getTotal() != null ? cart.getTotal() : 0.0);

            // Resolve user's default address for billing/shipping when userId present
            String billingAddressId = null;
            String shippingAddressId = null;
            if (order.getUserId() != null && !order.getUserId().isBlank()) {
                try {
                    CompletableFuture<ObjectNode> userFuture = entityService.getItem(
                        com.java_template.application.entity.user.version_1.User.ENTITY_NAME,
                        String.valueOf(com.java_template.application.entity.user.version_1.User.ENTITY_VERSION),
                        UUID.fromString(order.getUserId())
                    );
                    ObjectNode userNode = userFuture.join();
                    if (userNode != null && userNode.has("defaultAddressId")) {
                        billingAddressId = userNode.get("defaultAddressId").asText(null);
                        shippingAddressId = billingAddressId;
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to fetch user {} while creating order for cart {}: {}", order.getUserId(), cart.getId(), ex.getMessage());
                }
            }

            // If we couldn't determine addresses, fail checkout
            if (billingAddressId == null || billingAddressId.isBlank() || shippingAddressId == null || shippingAddressId.isBlank()) {
                logger.error("Cannot create Order for cart {}: missing billing/shipping address for user {}", cart.getId(), cart.getUserId());
                cart.setStatus("CheckoutFailed");
                return cart;
            }

            order.setBillingAddressId(billingAddressId);
            order.setShippingAddressId(shippingAddressId);

            // Build order items, fetching product name and currency when possible
            List<Order.OrderItem> orderItems = new ArrayList<>();
            String currency = null;
            for (Cart.Item cartItem : cart.getItems()) {
                if (cartItem == null || cartItem.getProductId() == null || cartItem.getProductId().isBlank()) {
                    logger.warn("Skipping invalid cart item in cart {}", cart.getId());
                    continue;
                }
                String productTechnicalId = cartItem.getProductId();
                String productName = null;
                try {
                    CompletableFuture<ObjectNode> prodFuture = entityService.getItem(
                        com.java_template.application.entity.product.version_1.Product.ENTITY_NAME,
                        String.valueOf(com.java_template.application.entity.product.version_1.Product.ENTITY_VERSION),
                        UUID.fromString(productTechnicalId)
                    );
                    ObjectNode prodNode = prodFuture.join();
                    if (prodNode != null) {
                        if (prodNode.has("name")) productName = prodNode.get("name").asText(null);
                        if (currency == null && prodNode.has("currency")) currency = prodNode.get("currency").asText(null);
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to fetch product {} while creating order for cart {}: {}", productTechnicalId, cart.getId(), ex.getMessage());
                }

                Order.OrderItem oi = new Order.OrderItem();
                oi.setProductId(productTechnicalId);
                oi.setQuantity(cartItem.getQuantity());
                oi.setUnitPrice(cartItem.getPriceAtAdd());
                // OrderItem requires non-blank name; use productName if available else fallback to productTechnicalId
                oi.setName(productName != null && !productName.isBlank() ? productName : productTechnicalId);
                orderItems.add(oi);
            }

            // Ensure currency is set (Order.isValid requires it)
            if (currency == null || currency.isBlank()) {
                currency = "USD";
            }
            order.setCurrency(currency);
            order.setItems(orderItems);

            // Final validation of constructed order before persisting
            if (!order.isValid()) {
                logger.error("Built order is invalid for cart {}. Aborting order creation.", cart.getId());
                cart.setStatus("CheckoutFailed");
                return cart;
            }

            // Persist order via EntityService (creating new entity will trigger order workflow)
            try {
                CompletableFuture<UUID> idFuture = entityService.addItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    order
                );
                UUID technicalId = idFuture.join();
                logger.info("Order created for cart {} with technicalId={}", cart.getId(), technicalId);
                // Keep cart in CheckedOut state to indicate successful transition
                cart.setStatus("CheckedOut");
            } catch (Exception ex) {
                logger.error("Failed to persist Order for cart {}: {}", cart.getId(), ex.getMessage(), ex);
                cart.setStatus("CheckoutFailed");
            }

        } catch (Exception ex) {
            logger.error("Unexpected error in CreateOrderProcessor for cart {}: {}", cart != null ? cart.getId() : "unknown", ex.getMessage(), ex);
            if (cart != null) cart.setStatus("CheckoutFailed");
        }
        return cart;
    }
}