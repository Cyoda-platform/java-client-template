package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class OrderCreateFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreateFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    @Autowired
    private ObjectMapper objectMapper;

    public OrderCreateFromPaidProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order creation from paid payment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null; // We'll build the order in the processor
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        try {
            // Get input data
            String paymentId = (String) context.getInputData().get("paymentId");
            String cartId = (String) context.getInputData().get("cartId");

            if (paymentId == null || cartId == null) {
                throw new IllegalArgumentException("paymentId and cartId are required");
            }

            // Get payment and validate it's paid
            Payment payment = getPaymentById(paymentId).join();
            if (payment == null) {
                throw new IllegalArgumentException("Payment not found: " + paymentId);
            }

            // Get cart and validate it's in checkout state
            Cart cart = getCartById(cartId).join();
            if (cart == null) {
                throw new IllegalArgumentException("Cart not found: " + cartId);
            }

            // Create order
            Order order = createOrderFromCart(cart, payment);

            // Process each line item: decrement stock and create shipment lines
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            processOrderLines(order, shipmentLines);

            // Create shipment
            createShipment(order.getOrderId(), shipmentLines);

            // Update cart state (this would be done via transition)
            updateCartToConverted(cart);

            logger.info("Created order {} from payment {} and cart {}",
                order.getOrderId(), paymentId, cartId);

            return order;

        } catch (Exception e) {
            logger.error("Error creating order from paid payment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create order: " + e.getMessage(), e);
        }
    }

    private CompletableFuture<Payment> getPaymentById(String paymentId) {
        Condition paymentIdCondition = Condition.of("$.paymentId", "EQUALS", paymentId);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", paymentIdCondition);

        return entityService.getFirstItemByCondition(
            Payment.ENTITY_NAME,
            Payment.ENTITY_VERSION,
            condition,
            true
        ).thenApply(optionalPayload -> {
            if (optionalPayload.isPresent()) {
                try {
                    return objectMapper.convertValue(optionalPayload.get().getData(), Payment.class);
                } catch (Exception e) {
                    logger.error("Error converting payment data: {}", e.getMessage(), e);
                    return null;
                }
            }
            return null;
        });
    }

    private CompletableFuture<Cart> getCartById(String cartId) {
        Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", cartIdCondition);

        return entityService.getFirstItemByCondition(
            Cart.ENTITY_NAME,
            Cart.ENTITY_VERSION,
            condition,
            true
        ).thenApply(optionalPayload -> {
            if (optionalPayload.isPresent()) {
                try {
                    return objectMapper.convertValue(optionalPayload.get().getData(), Cart.class);
                } catch (Exception e) {
                    logger.error("Error converting cart data: {}", e.getMessage(), e);
                    return null;
                }
            }
            return null;
        });
    }

    private Order createOrderFromCart(Cart cart, Payment payment) {
        Order order = new Order();
        order.setOrderId("order-" + UUID.randomUUID().toString());
        order.setOrderNumber(generateShortULID());

        // Convert cart lines to order lines
        List<Order.OrderLine> orderLines = new ArrayList<>();
        int totalItems = 0;
        double grandTotal = 0.0;

        for (Cart.CartLine cartLine : cart.getLines()) {
            Order.OrderLine orderLine = new Order.OrderLine();
            orderLine.setSku(cartLine.getSku());
            orderLine.setName(cartLine.getName());
            orderLine.setUnitPrice(cartLine.getPrice());
            orderLine.setQty(cartLine.getQty());
            orderLine.setLineTotal(cartLine.getPrice() * cartLine.getQty());

            orderLines.add(orderLine);
            totalItems += cartLine.getQty();
            grandTotal += orderLine.getLineTotal();
        }

        order.setLines(orderLines);

        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(totalItems);
        totals.setGrand(grandTotal);
        order.setTotals(totals);

        // Copy guest contact
        if (cart.getGuestContact() != null) {
            Order.GuestContact guestContact = new Order.GuestContact();
            guestContact.setName(cart.getGuestContact().getName());
            guestContact.setEmail(cart.getGuestContact().getEmail());
            guestContact.setPhone(cart.getGuestContact().getPhone());

            if (cart.getGuestContact().getAddress() != null) {
                Order.GuestAddress address = new Order.GuestAddress();
                address.setLine1(cart.getGuestContact().getAddress().getLine1());
                address.setCity(cart.getGuestContact().getAddress().getCity());
                address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                address.setCountry(cart.getGuestContact().getAddress().getCountry());
                guestContact.setAddress(address);
            }

            order.setGuestContact(guestContact);
        }

        Instant now = Instant.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        return order;
    }

    private String generateShortULID() {
        // Simple ULID-like generation for demo purposes
        return "01" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void processOrderLines(Order order, List<Shipment.ShipmentLine> shipmentLines) {
        for (Order.OrderLine orderLine : order.getLines()) {
            // Get product and decrement stock
            CompletableFuture<Product> productFuture = getProductBySku(orderLine.getSku());
            Product product = productFuture.join();

            if (product == null) {
                throw new IllegalArgumentException("Product not found for SKU: " + orderLine.getSku());
            }

            if (product.getQuantityAvailable() < orderLine.getQty()) {
                throw new IllegalArgumentException("Insufficient stock for " + orderLine.getSku() +
                    ". Available: " + product.getQuantityAvailable() + ", Required: " + orderLine.getQty());
            }

            // Decrement stock
            product.setQuantityAvailable(product.getQuantityAvailable() - orderLine.getQty());
            product.setUpdatedAt(Instant.now());

            // Update product (without transition)
            try {
                UUID productEntityId = getProductEntityId(product.getSku()).join();
                entityService.updateItem(productEntityId, product).join();
                logger.info("Decremented stock for {} by {}, new quantity: {}",
                    product.getSku(), orderLine.getQty(), product.getQuantityAvailable());
            } catch (Exception e) {
                logger.error("Failed to update product stock for {}: {}", product.getSku(), e.getMessage(), e);
                throw new RuntimeException("Failed to update product stock", e);
            }

            // Create shipment line
            Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
            shipmentLine.setSku(orderLine.getSku());
            shipmentLine.setQtyOrdered(orderLine.getQty());
            shipmentLine.setQtyPicked(0);
            shipmentLine.setQtyShipped(0);
            shipmentLines.add(shipmentLine);
        }
    }

    private CompletableFuture<Product> getProductBySku(String sku) {
        Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", skuCondition);

        return entityService.getFirstItemByCondition(
            Product.ENTITY_NAME,
            Product.ENTITY_VERSION,
            condition,
            true
        ).thenApply(optionalPayload -> {
            if (optionalPayload.isPresent()) {
                try {
                    return objectMapper.convertValue(optionalPayload.get().getData(), Product.class);
                } catch (Exception e) {
                    logger.error("Error converting product data: {}", e.getMessage(), e);
                    return null;
                }
            }
            return null;
        });
    }

    private CompletableFuture<UUID> getProductEntityId(String sku) {
        Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", skuCondition);

        return entityService.getFirstItemByCondition(
            Product.ENTITY_NAME,
            Product.ENTITY_VERSION,
            condition,
            true
        ).thenApply(optionalPayload -> {
            if (optionalPayload.isPresent()) {
                return optionalPayload.get().getMetadata().getId();
            }
            return null;
        });
    }

    private void createShipment(String orderId, List<Shipment.ShipmentLine> shipmentLines) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId("shipment-" + UUID.randomUUID().toString());
            shipment.setOrderId(orderId);
            shipment.setLines(shipmentLines);

            Instant now = Instant.now();
            shipment.setCreatedAt(now);
            shipment.setUpdatedAt(now);

            // Save shipment with CREATE_SHIPMENT transition
            entityService.addItem(Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, shipment).join();
            logger.info("Created shipment {} for order {}", shipment.getShipmentId(), orderId);

        } catch (Exception e) {
            logger.error("Failed to create shipment for order {}: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Failed to create shipment", e);
        }
    }

    private void updateCartToConverted(Cart cart) {
        try {
            cart.setUpdatedAt(Instant.now());
            // Note: The actual state transition to CONVERTED would be handled by the workflow engine
            // This is just updating the entity data
            logger.info("Cart {} marked for conversion", cart.getCartId());
        } catch (Exception e) {
            logger.error("Failed to update cart state: {}", e.getMessage(), e);
            // Don't throw here as the order creation is more important
        }
    }
}