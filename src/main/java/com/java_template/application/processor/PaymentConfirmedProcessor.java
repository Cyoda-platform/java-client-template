package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.Order.OrderItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class PaymentConfirmedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentConfirmedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PaymentConfirmedProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PaymentConfirmed for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart state for payment confirmation")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && cart.isValid();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        // Simulate payment result via request payload
        ObjectNode payload = (ObjectNode) context.request().getPayload();
        boolean success = false;
        if (payload != null && payload.has("paymentSuccess")) {
            success = payload.get("paymentSuccess").asBoolean(false);
        }

        try {
            if (success) {
                // Create Order
                Order order = new Order();
                order.setOrderId("order_" + cart.getCartId());
                order.setUserId(cart.getUserId());
                order.setCartId(cart.getCartId());
                List<OrderItem> items = new ArrayList<>();
                if (cart.getItems() != null) {
                    for (com.java_template.application.entity.cart.version_1.Cart.CartItem ci : cart.getItems()) {
                        OrderItem oi = new OrderItem();
                        oi.setProductId(ci.getProductId());
                        oi.setQty(ci.getQty());
                        oi.setPrice(ci.getPriceAtAdd());
                        items.add(oi);
                    }
                }
                order.setItems(items);
                order.setSubtotal(cart.getSubtotal());
                order.setShipping(0.0);
                order.setTotal(cart.getTotal());
                order.setShippingAddressId(null);
                order.setBillingAddressId(null);
                order.setPaymentStatus("PAID");
                order.setFulfillmentStatus("CREATED");

                CompletableFuture<UUID> created = entityService.addItem(Order.ENTITY_NAME, String.valueOf(Order.ENTITY_VERSION), order);
                UUID createdId = created.get();
                logger.info("Order created with technicalId {} for cart {}", createdId, cart.getCartId());

                // Finalize cart
                cart.setStatus("CHECKED_OUT");
                logger.info("Cart {} marked as CHECKED_OUT and order {} created", cart.getCartId(), order.getOrderId());

            } else {
                // Release reserved inventory - here we assume inventory was decremented during reservation, so we need to add back
                if (cart.getItems() != null) {
                    for (com.java_template.application.entity.cart.version_1.Cart.CartItem item : cart.getItems()) {
                        CompletableFuture<ArrayNode> productSearchFuture = entityService.getItemsByCondition(
                            Product.ENTITY_NAME,
                            String.valueOf(Product.ENTITY_VERSION),
                            SearchConditionRequest.group("AND", Condition.of("$.productId", "EQUALS", item.getProductId())),
                            true
                        );
                        ArrayNode products = productSearchFuture.get();
                        if (products != null && products.size() > 0) {
                            ObjectNode productNode = (ObjectNode) products.get(0);
                            Product product = objectMapper.convertValue(productNode, Product.class);
                            product.setInventory(product.getInventory() + item.getQty());
                            UUID technicalId = null;
                            if (productNode.has("technicalId")) {
                                try { technicalId = UUID.fromString(productNode.get("technicalId").asText()); } catch (Exception ex) { technicalId = null; }
                            }
                            if (technicalId != null) {
                                entityService.updateItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), technicalId, product).get();
                            }
                        }
                    }
                }
                cart.setStatus("OPEN");
                logger.info("Payment failed for cart {}. Cart reopened and inventory released", cart.getCartId());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while processing payment confirmation for cart {}", cart.getCartId(), e);
            throw new RuntimeException(e);
        }

        return cart;
    }
}
