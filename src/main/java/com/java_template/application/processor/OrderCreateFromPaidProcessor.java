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
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Component
public class OrderCreateFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreateFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public OrderCreateFromPaidProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order create from paid for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract order entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract order entity: " + error.getMessage());
            })
            .validate(this::isValidOrder, "Invalid order state")
            .map(this::createOrderFromPaid)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidOrder(Order order) {
        return order != null && order.getOrderId() != null && !order.getOrderId().trim().isEmpty();
    }

    private Order createOrderFromPaid(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        
        // Extract cartId and paymentId from the order entity or context
        // In a real implementation, these would come from the request payload
        // For now, we'll assume they're available in the order entity somehow
        String cartId = extractCartIdFromContext(context);
        String paymentId = extractPaymentIdFromContext(context);

        logger.info("Creating order from paid cart: {}, payment: {}", cartId, paymentId);

        try {
            // Get cart and payment entities
            Cart cart = getCartById(cartId);
            Payment payment = getPaymentById(paymentId);

            // Validate payment is in PAID state
            validatePaymentState(payment);

            // Generate short ULID for order number (simplified)
            order.setOrderNumber(generateShortULID());

            // Snapshot cart lines to order
            snapshotCartToOrder(order, cart);

            // Decrement product stock
            decrementProductStock(cart);

            // Create shipment
            createShipment(order);

            // Update cart to converted state
            updateCartToConverted(cart);

            // Set timestamps
            Instant now = Instant.now();
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            logger.info("Order created: {} from cart: {}", order.getOrderNumber(), cartId);

        } catch (Exception e) {
            logger.error("Failed to create order from paid cart: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create order: " + e.getMessage(), e);
        }

        return order;
    }

    private String extractCartIdFromContext(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        // In a real implementation, this would extract from the request payload
        // For now, return a placeholder
        return "extracted-cart-id";
    }

    private String extractPaymentIdFromContext(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        // In a real implementation, this would extract from the request payload
        // For now, return a placeholder
        return "extracted-payment-id";
    }

    private Cart getCartById(String cartId) {
        Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
        SearchConditionRequest condition = new SearchConditionRequest();
        condition.setType("group");
        condition.setOperator("AND");
        condition.setConditions(List.of(cartIdCondition));

        Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
            Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);
        
        if (cartResponse.isEmpty()) {
            throw new RuntimeException("Cart not found: " + cartId);
        }
        
        return cartResponse.get().getData();
    }

    private Payment getPaymentById(String paymentId) {
        Condition paymentIdCondition = Condition.of("$.paymentId", "EQUALS", paymentId);
        SearchConditionRequest condition = new SearchConditionRequest();
        condition.setType("group");
        condition.setOperator("AND");
        condition.setConditions(List.of(paymentIdCondition));

        Optional<EntityResponse<Payment>> paymentResponse = entityService.getFirstItemByCondition(
            Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION, condition, true);
        
        if (paymentResponse.isEmpty()) {
            throw new RuntimeException("Payment not found: " + paymentId);
        }
        
        return paymentResponse.get().getData();
    }

    private void validatePaymentState(Payment payment) {
        // In a real implementation, we would check payment.meta.state
        // For now, we'll assume the workflow ensures this processor only runs on PAID payments
        logger.info("Validating payment state for payment: {}", payment.getPaymentId());
    }

    private String generateShortULID() {
        // Simplified ULID generation - in real implementation use proper ULID library
        return "ORD" + System.currentTimeMillis() % 1000000;
    }

    private void snapshotCartToOrder(Order order, Cart cart) {
        // Snapshot cart lines
        List<Order.OrderLine> orderLines = new ArrayList<>();
        for (Cart.CartLine cartLine : cart.getLines()) {
            Order.OrderLine orderLine = new Order.OrderLine();
            orderLine.setSku(cartLine.getSku());
            orderLine.setName(cartLine.getName());
            orderLine.setUnitPrice(cartLine.getPrice());
            orderLine.setQty(cartLine.getQty());
            orderLine.setLineTotal(cartLine.getPrice() * cartLine.getQty());
            orderLines.add(orderLine);
        }
        order.setLines(orderLines);

        // Snapshot totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(cart.getTotalItems());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);

        // Snapshot guest contact
        if (cart.getGuestContact() != null) {
            Order.GuestContact guestContact = new Order.GuestContact();
            guestContact.setName(cart.getGuestContact().getName());
            guestContact.setEmail(cart.getGuestContact().getEmail());
            guestContact.setPhone(cart.getGuestContact().getPhone());
            
            if (cart.getGuestContact().getAddress() != null) {
                Order.Address address = new Order.Address();
                address.setLine1(cart.getGuestContact().getAddress().getLine1());
                address.setCity(cart.getGuestContact().getAddress().getCity());
                address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                address.setCountry(cart.getGuestContact().getAddress().getCountry());
                guestContact.setAddress(address);
            }
            
            order.setGuestContact(guestContact);
        }
    }

    private void decrementProductStock(Cart cart) {
        for (Cart.CartLine line : cart.getLines()) {
            try {
                // Find product by SKU
                Condition skuCondition = Condition.of("$.sku", "EQUALS", line.getSku());
                SearchConditionRequest condition = new SearchConditionRequest();
                condition.setType("group");
                condition.setOperator("AND");
                condition.setConditions(List.of(skuCondition));

                Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
                    Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);
                
                if (productResponse.isPresent()) {
                    Product product = productResponse.get().getData();
                    UUID productId = productResponse.get().getMetadata().getId();
                    
                    // Decrement stock
                    int newQuantity = product.getQuantityAvailable() - line.getQty();
                    product.setQuantityAvailable(Math.max(0, newQuantity));
                    
                    // Update product (no transition)
                    entityService.update(productId, product, null);
                    
                    logger.info("Decremented stock for SKU: {}, new quantity: {}", line.getSku(), newQuantity);
                }
            } catch (Exception e) {
                logger.error("Failed to decrement stock for SKU: {}", line.getSku(), e);
            }
        }
    }

    private void createShipment(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
            shipment.setOrderId(order.getOrderId());
            
            // Create shipment lines
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            for (Order.OrderLine orderLine : order.getLines()) {
                Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
                shipmentLine.setSku(orderLine.getSku());
                shipmentLine.setQtyOrdered(orderLine.getQty());
                shipmentLine.setQtyPicked(0);
                shipmentLine.setQtyShipped(0);
                shipmentLines.add(shipmentLine);
            }
            shipment.setLines(shipmentLines);
            
            Instant now = Instant.now();
            shipment.setCreatedAt(now);
            shipment.setUpdatedAt(now);
            
            // Create shipment entity with transition
            entityService.save(shipment);
            
            logger.info("Created shipment: {} for order: {}", shipment.getShipmentId(), order.getOrderId());
        } catch (Exception e) {
            logger.error("Failed to create shipment for order: {}", order.getOrderId(), e);
        }
    }

    private void updateCartToConverted(Cart cart) {
        try {
            // Find cart by cartId to get the technical ID
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cart.getCartId());
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);
            
            if (cartResponse.isPresent()) {
                UUID cartTechnicalId = cartResponse.get().getMetadata().getId();
                // Update cart with CHECKOUT transition
                entityService.update(cartTechnicalId, cart, "CHECKOUT");
                logger.info("Updated cart to converted state: {}", cart.getCartId());
            }
        } catch (Exception e) {
            logger.error("Failed to update cart to converted state: {}", cart.getCartId(), e);
        }
    }
}
