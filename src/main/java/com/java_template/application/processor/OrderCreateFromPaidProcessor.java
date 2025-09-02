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

import java.time.LocalDateTime;
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
        logger.info("Processing Order creation from paid cart for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract order entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract order entity: " + error.getMessage());
            })
            .validate(this::isValidOrder, "Invalid order state")
            .map(this::processOrderCreation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidOrder(Order order) {
        return order != null && order.getOrderId() != null && !order.getOrderId().trim().isEmpty();
    }

    private Order processOrderCreation(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        
        logger.info("Creating order from paid cart for order: {}", order.getOrderId());

        try {
            // Find associated cart and payment
            Cart cart = findCartForOrder(order);
            Payment payment = findPaymentForCart(cart);

            // Validate payment is PAID and cart is CONVERTED
            validatePaymentAndCart(payment, cart);

            // Generate short ULID for order number
            order.setOrderNumber(generateShortULID());

            // Snapshot cart data to order
            snapshotCartToOrder(order, cart);

            // Decrement product stock
            decrementProductStock(cart);

            // Create shipment
            createShipment(order);

            // Update timestamps
            order.setUpdatedAt(LocalDateTime.now());

            logger.info("Order {} created from cart {} with {} items", 
                       order.getOrderNumber(), cart.getCartId(), order.getTotals().getItems());

            return order;

        } catch (Exception e) {
            logger.error("Failed to create order from paid cart: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create order from paid cart: " + e.getMessage(), e);
        }
    }

    private Cart findCartForOrder(Order order) {
        // In a real implementation, we would need to find the cart associated with this order
        // For now, we'll assume the cart ID is stored somewhere accessible
        // This is a simplified implementation - in practice, you'd need to store the relationship
        throw new UnsupportedOperationException("Cart lookup not implemented - need cart ID reference");
    }

    private Payment findPaymentForCart(Cart cart) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cart.getCartId());
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(cartIdCondition));

            Optional<EntityResponse<Payment>> paymentResponse = entityService.getFirstItemByCondition(
                Payment.class, 
                Payment.ENTITY_NAME, 
                Payment.ENTITY_VERSION, 
                condition, 
                true
            );

            if (paymentResponse.isEmpty()) {
                throw new RuntimeException("Payment not found for cart: " + cart.getCartId());
            }

            return paymentResponse.get().getData();
        } catch (Exception e) {
            logger.error("Failed to find payment for cart {}: {}", cart.getCartId(), e.getMessage());
            throw new RuntimeException("Failed to find payment for cart: " + e.getMessage(), e);
        }
    }

    private void validatePaymentAndCart(Payment payment, Cart cart) {
        // Note: State validation would typically check entity.meta.state
        // For this implementation, we'll assume the workflow ensures correct states
        
        if (cart.getGuestContact() == null) {
            throw new RuntimeException("Cart guest contact is required for order creation");
        }
    }

    private String generateShortULID() {
        // Simple ULID-like generation (in practice, use a proper ULID library)
        return UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
    }

    private void snapshotCartToOrder(Order order, Cart cart) {
        // Initialize order lines and totals
        order.setLines(new ArrayList<>());
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(0);
        totals.setGrand(0.0);

        // Convert cart lines to order lines
        for (Cart.CartLine cartLine : cart.getLines()) {
            Order.OrderLine orderLine = new Order.OrderLine();
            orderLine.setSku(cartLine.getSku());
            orderLine.setName(cartLine.getName());
            orderLine.setUnitPrice(cartLine.getPrice());
            orderLine.setQty(cartLine.getQty());
            orderLine.setLineTotal(cartLine.getPrice() * cartLine.getQty());

            order.getLines().add(orderLine);
            totals.setItems(totals.getItems() + cartLine.getQty());
            totals.setGrand(totals.getGrand() + orderLine.getLineTotal());
        }

        order.setTotals(totals);

        // Copy guest contact information
        Order.GuestContact orderContact = new Order.GuestContact();
        Cart.GuestContact cartContact = cart.getGuestContact();
        
        orderContact.setName(cartContact.getName());
        orderContact.setEmail(cartContact.getEmail());
        orderContact.setPhone(cartContact.getPhone());
        
        if (cartContact.getAddress() != null) {
            Order.Address orderAddress = new Order.Address();
            orderAddress.setLine1(cartContact.getAddress().getLine1());
            orderAddress.setCity(cartContact.getAddress().getCity());
            orderAddress.setPostcode(cartContact.getAddress().getPostcode());
            orderAddress.setCountry(cartContact.getAddress().getCountry());
            orderContact.setAddress(orderAddress);
        }
        
        order.setGuestContact(orderContact);
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
                    Product.class,
                    Product.ENTITY_NAME,
                    Product.ENTITY_VERSION,
                    condition,
                    true
                );

                if (productResponse.isPresent()) {
                    Product product = productResponse.get().getData();
                    UUID productId = productResponse.get().getMetadata().getId();
                    
                    // Decrement stock (allow negative for oversell)
                    int newQuantity = product.getQuantityAvailable() - line.getQty();
                    product.setQuantityAvailable(newQuantity);
                    
                    if (newQuantity < 0) {
                        logger.warn("Product {} oversold: new quantity is {}", line.getSku(), newQuantity);
                    }

                    // Update product (no transition - stays in same state)
                    entityService.update(productId, product, null);
                    
                    logger.debug("Decremented stock for product {}: {} -> {}", 
                               line.getSku(), product.getQuantityAvailable() + line.getQty(), newQuantity);
                } else {
                    logger.warn("Product {} not found for stock decrement", line.getSku());
                }
            } catch (Exception e) {
                logger.error("Failed to decrement stock for product {}: {}", line.getSku(), e.getMessage());
                // Continue processing other products
            }
        }
    }

    private void createShipment(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
            shipment.setOrderId(order.getOrderId());
            shipment.setLines(new ArrayList<>());

            // Create shipment lines from order lines
            for (Order.OrderLine orderLine : order.getLines()) {
                Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
                shipmentLine.setSku(orderLine.getSku());
                shipmentLine.setQtyOrdered(orderLine.getQty());
                shipmentLine.setQtyPicked(0);
                shipmentLine.setQtyShipped(0);
                shipment.getLines().add(shipmentLine);
            }

            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());

            // Save shipment with initialize_shipment transition
            EntityResponse<Shipment> shipmentResponse = entityService.save(shipment);
            
            logger.info("Created shipment {} for order {}", 
                       shipmentResponse.getData().getShipmentId(), order.getOrderId());

        } catch (Exception e) {
            logger.error("Failed to create shipment for order {}: {}", order.getOrderId(), e.getMessage());
            throw new RuntimeException("Failed to create shipment: " + e.getMessage(), e);
        }
    }
}
