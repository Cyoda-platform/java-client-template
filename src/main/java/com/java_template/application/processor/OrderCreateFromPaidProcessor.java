package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderCreateFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreateFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderCreateFromPaidProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order creation from paid payment for request: {}", request.getId());

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
        EntityProcessorCalculationRequest request = context.request();
        
        logger.info("Creating order from paid payment for order: {}", order.getOrderId());
        
        try {
            // CRITICAL: The order entity already contains all the data we need
            // Never extract from request payload - use order getters directly
            
            // For this processor, we need to get cartId and paymentId from somewhere
            // Since this is triggered by payment workflow, we can get these from the order entity
            // or we might need to look them up based on the order data
            
            // For demo purposes, let's assume the order has references we can use
            // In a real implementation, these would come from the workflow context
            String cartId = extractCartIdFromOrder(order);
            String paymentId = extractPaymentIdFromOrder(order);
            
            if (cartId == null || paymentId == null) {
                logger.error("Cannot find cart or payment references for order {}", order.getOrderId());
                throw new RuntimeException("Missing cart or payment references");
            }
            
            // 1. Retrieve and validate cart
            Cart cart = retrieveCart(cartId);
            if (cart == null) {
                throw new RuntimeException("Cart not found: " + cartId);
            }
            
            // 2. Retrieve and validate payment
            Payment payment = retrievePayment(paymentId);
            if (payment == null) {
                throw new RuntimeException("Payment not found: " + paymentId);
            }
            
            // 3. Generate short ULID for order number (simplified)
            String orderNumber = generateShortOrderNumber();
            order.setOrderNumber(orderNumber);
            
            // 4. Snapshot cart data to order
            snapshotCartToOrder(cart, order);
            
            // 5. Decrement product stock for each line
            decrementProductStock(order.getLines());
            
            // 6. Create shipment
            createShipment(order);
            
            // 7. Update timestamps
            order.setUpdatedAt(Instant.now());
            
            logger.info("Order {} created successfully from cart {} and payment {}", 
                order.getOrderId(), cartId, paymentId);
            
            return order;
            
        } catch (Exception e) {
            logger.error("Failed to create order from paid payment: {}", e.getMessage(), e);
            throw new RuntimeException("Order creation failed: " + e.getMessage(), e);
        }
    }
    
    private String extractCartIdFromOrder(Order order) {
        // In a real implementation, this would come from workflow context or order metadata
        // For demo, we'll assume it's stored somewhere accessible
        // This is a simplified approach - in practice, this data would be passed through the workflow
        return "demo-cart-id"; // Placeholder
    }
    
    private String extractPaymentIdFromOrder(Order order) {
        // Similar to cart ID, this would come from workflow context
        return "demo-payment-id"; // Placeholder
    }
    
    private Cart retrieveCart(String cartId) {
        try {
            Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", cartIdCondition);
            
            Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
                Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);
                
            return cartResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to retrieve cart {}: {}", cartId, e.getMessage());
            return null;
        }
    }
    
    private Payment retrievePayment(String paymentId) {
        try {
            Condition paymentIdCondition = Condition.of("$.paymentId", "EQUALS", paymentId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", paymentIdCondition);
            
            Optional<EntityResponse<Payment>> paymentResponse = entityService.getFirstItemByCondition(
                Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION, condition, true);
                
            return paymentResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to retrieve payment {}: {}", paymentId, e.getMessage());
            return null;
        }
    }
    
    private String generateShortOrderNumber() {
        // Simplified ULID generation - in practice, use a proper ULID library
        return "ORD" + System.currentTimeMillis() % 1000000;
    }
    
    private void snapshotCartToOrder(Cart cart, Order order) {
        // Convert cart lines to order lines
        List<Order.OrderLine> orderLines = new ArrayList<>();
        if (cart.getLines() != null) {
            for (Cart.CartLine cartLine : cart.getLines()) {
                Order.OrderLine orderLine = new Order.OrderLine();
                orderLine.setSku(cartLine.getSku());
                orderLine.setName(cartLine.getName());
                orderLine.setUnitPrice(cartLine.getPrice());
                orderLine.setQty(cartLine.getQty());
                orderLine.calculateLineTotal();
                orderLines.add(orderLine);
            }
        }
        order.setLines(orderLines);
        
        // Copy totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(cart.getGrandTotal());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);
        
        // Copy guest contact
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
    
    private void decrementProductStock(List<Order.OrderLine> lines) {
        if (lines == null) return;
        
        for (Order.OrderLine line : lines) {
            try {
                // Retrieve product by SKU
                Condition skuCondition = Condition.of("$.sku", "EQUALS", line.getSku());
                SearchConditionRequest condition = SearchConditionRequest.group("AND", skuCondition);
                
                Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
                    Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);
                
                if (productResponse.isPresent()) {
                    Product product = productResponse.get().getData();
                    UUID productId = productResponse.get().getMetadata().getId();
                    
                    // Validate sufficient stock
                    if (product.getQuantityAvailable() < line.getQty()) {
                        throw new RuntimeException("Insufficient stock for product " + line.getSku() + 
                            ". Available: " + product.getQuantityAvailable() + ", Required: " + line.getQty());
                    }
                    
                    // Decrement stock
                    product.setQuantityAvailable(product.getQuantityAvailable() - line.getQty());
                    
                    // Update product
                    entityService.update(productId, product, null);
                    
                    logger.info("Decremented stock for product {}: {} units", line.getSku(), line.getQty());
                } else {
                    throw new RuntimeException("Product not found: " + line.getSku());
                }
            } catch (Exception e) {
                logger.error("Failed to decrement stock for product {}: {}", line.getSku(), e.getMessage());
                throw new RuntimeException("Stock decrement failed for " + line.getSku(), e);
            }
        }
    }
    
    private void createShipment(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId("SHIP-" + order.getOrderId());
            shipment.setOrderId(order.getOrderId());
            shipment.setCreatedAt(Instant.now());
            shipment.setUpdatedAt(Instant.now());
            
            // Create shipment lines from order lines
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            if (order.getLines() != null) {
                for (Order.OrderLine orderLine : order.getLines()) {
                    Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
                    shipmentLine.setSku(orderLine.getSku());
                    shipmentLine.setQtyOrdered(orderLine.getQty());
                    shipmentLine.setQtyPicked(0); // Initially not picked
                    shipmentLine.setQtyShipped(0); // Initially not shipped
                    shipmentLines.add(shipmentLine);
                }
            }
            shipment.setLines(shipmentLines);
            
            // Save shipment
            entityService.save(shipment);
            
            logger.info("Created shipment {} for order {}", shipment.getShipmentId(), order.getOrderId());
            
        } catch (Exception e) {
            logger.error("Failed to create shipment for order {}: {}", order.getOrderId(), e.getMessage());
            throw new RuntimeException("Shipment creation failed", e);
        }
    }
}
