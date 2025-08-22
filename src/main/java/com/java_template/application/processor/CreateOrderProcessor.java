package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.address.version_1.Address;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.Item;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.Order.OrderItem;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
    private final ObjectMapper objectMapper;

    public CreateOrderProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid entity state")
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
            // Only process carts that are in CheckedOut state (defensive)
            if (cart.getStatus() == null || !"CheckedOut".equalsIgnoreCase(cart.getStatus())) {
                logger.info("Cart {} not in CheckedOut state, skipping CreateOrderProcessor", cart.getId());
                return cart;
            }

            // Build order snapshot
            Order order = new Order();
            order.setId(UUID.randomUUID().toString()); // new business id for order
            order.setUserId(cart.getUserId());
            order.setCreatedAt(cart.getCreatedAt() != null ? cart.getCreatedAt() : java.time.Instant.now().toString());
            order.setStatus("Created");
            order.setPaymentStatus("Pending");
            order.setTotal(cart.getTotal());

            List<OrderItem> orderItems = new ArrayList<>();
            String currency = null;
            // For each cart item, fetch product info to populate name & currency
            if (cart.getItems() != null) {
                for (Item cartItem : cart.getItems()) {
                    if (cartItem == null) continue;
                    String productId = cartItem.getProductId();
                    String productName = null;
                    try {
                        CompletableFuture<ObjectNode> prodFuture = entityService.getItem(
                            Product.ENTITY_NAME,
                            String.valueOf(Product.ENTITY_VERSION),
                            UUID.fromString(productId)
                        );
                        ObjectNode prodNode = prodFuture != null ? prodFuture.join() : null;
                        if (prodNode != null) {
                            JsonNode nameNode = prodNode.get("name");
                            JsonNode currencyNode = prodNode.get("currency");
                            if (nameNode != null && !nameNode.isNull()) productName = nameNode.asText();
                            if (currency == null && currencyNode != null && !currencyNode.isNull()) {
                                currency = currencyNode.asText();
                            }
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to fetch product {} while creating order for cart {}: {}", productId, cart.getId(), ex.getMessage());
                    }

                    OrderItem oi = new OrderItem();
                    oi.setProductId(productId);
                    oi.setQuantity(cartItem.getQuantity());
                    oi.setUnitPrice(cartItem.getPriceAtAdd());
                    oi.setName(productName != null ? productName : "");
                    orderItems.add(oi);
                }
            }

            // Determine currency, default if necessary
            if (currency == null || currency.isBlank()) {
                currency = "USD";
            }
            order.setCurrency(currency);
            order.setItems(orderItems);

            // Resolve billing & shipping address:
            String billingAddressId = null;
            String shippingAddressId = null;

            if (order.getUserId() != null && !order.getUserId().isBlank()) {
                try {
                    CompletableFuture<ObjectNode> userFuture = entityService.getItem(
                        User.ENTITY_NAME,
                        String.valueOf(User.ENTITY_VERSION),
                        UUID.fromString(order.getUserId())
                    );
                    ObjectNode userNode = userFuture != null ? userFuture.join() : null;
                    if (userNode != null && userNode.hasNonNull("defaultAddressId")) {
                        String defaultAddr = userNode.get("defaultAddressId").asText();
                        if (defaultAddr != null && !defaultAddr.isBlank()) {
                            billingAddressId = defaultAddr;
                            shippingAddressId = defaultAddr;
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to fetch user {} while creating order for cart {}: {}", order.getUserId(), cart.getId(), ex.getMessage());
                }

                // If user had no defaultAddressId, try to find any default address or any address for the user
                if ((billingAddressId == null || billingAddressId.isBlank())) {
                    try {
                        SearchConditionRequest cond = SearchConditionRequest.group("AND",
                            Condition.of("$.userId", "EQUALS", order.getUserId()),
                            Condition.of("$.isDefault", "EQUALS", "true")
                        );
                        CompletableFuture<ArrayNode> addrsFuture = entityService.getItemsByCondition(
                            Address.ENTITY_NAME,
                            String.valueOf(Address.ENTITY_VERSION),
                            cond,
                            true
                        );
                        ArrayNode addrNodes = addrsFuture != null ? addrsFuture.join() : null;
                        if (addrNodes != null && addrNodes.size() > 0) {
                            JsonNode a = addrNodes.get(0);
                            if (a != null && a.hasNonNull("id")) {
                                billingAddressId = a.get("id").asText();
                                shippingAddressId = billingAddressId;
                            }
                        } else {
                            // Try any address for the user
                            SearchConditionRequest condAny = SearchConditionRequest.group("AND",
                                Condition.of("$.userId", "EQUALS", order.getUserId())
                            );
                            CompletableFuture<ArrayNode> anyAddrFuture = entityService.getItemsByCondition(
                                Address.ENTITY_NAME,
                                String.valueOf(Address.ENTITY_VERSION),
                                condAny,
                                true
                            );
                            ArrayNode anyAddrs = anyAddrFuture != null ? anyAddrFuture.join() : null;
                            if (anyAddrs != null && anyAddrs.size() > 0) {
                                JsonNode a = anyAddrs.get(0);
                                if (a != null && a.hasNonNull("id")) {
                                    billingAddressId = a.get("id").asText();
                                    shippingAddressId = billingAddressId;
                                }
                            }
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to search addresses for user {}: {}", order.getUserId(), ex.getMessage());
                    }
                }
            }

            // If we could not resolve addresses, mark cart as failed and stop processing
            if (billingAddressId == null || billingAddressId.isBlank() || shippingAddressId == null || shippingAddressId.isBlank()) {
                logger.error("Cannot create Order for cart {}: missing billing/shipping address for user {}", cart.getId(), cart.getUserId());
                cart.setStatus("CheckoutFailed");
                return cart;
            }

            order.setBillingAddressId(billingAddressId);
            order.setShippingAddressId(shippingAddressId);

            // Final validation of Order before persistence
            if (!order.isValid()) {
                logger.error("Built order is invalid for cart {}. Aborting order creation.", cart.getId());
                cart.setStatus("CheckoutFailed");
                return cart;
            }

            // Persist order (create new entity) - this will trigger Order workflow
            try {
                CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    order
                );
                java.util.UUID technicalId = idFuture != null ? idFuture.join() : null;
                logger.info("Order created for cart {} with technicalId={}", cart.getId(), technicalId);
            } catch (Exception ex) {
                logger.error("Failed to persist Order for cart {}: {}", cart.getId(), ex.getMessage());
                cart.setStatus("CheckoutFailed");
                return cart;
            }

            // Optionally keep cart status as CheckedOut or mark as Converted
            cart.setStatus("CheckedOut");
            return cart;

        } catch (Exception ex) {
            logger.error("Unexpected error in CreateOrderProcessor for cart {}: {}", cart != null ? cart.getId() : "unknown", ex.getMessage(), ex);
            if (cart != null) {
                cart.setStatus("CheckoutFailed");
                return cart;
            }
            return null;
        }
    }
}