package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
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
        logger.info("Processing Order create from paid for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract order entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract order entity: " + error.getMessage());
            })
            .validate(this::isValidOrder, "Invalid order state")
            .map(this::processCreateFromPaid)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidOrder(Order order) {
        return order != null && order.isValid();
    }

    private Order processCreateFromPaid(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        
        // Extract payment and cart IDs from context
        String paymentId = extractPaymentIdFromContext(context);
        String cartId = extractCartIdFromContext(context);
        
        if (paymentId == null || cartId == null) {
            throw new IllegalArgumentException("Payment ID and Cart ID are required");
        }
        
        logger.info("Creating order from paid payment: {} and cart: {}", paymentId, cartId);
        
        // Validate payment exists and is in 'paid' state
        Payment payment = getPaymentById(paymentId);
        if (payment == null) {
            throw new IllegalStateException("Payment not found: " + paymentId);
        }
        
        // Validate cart exists and is in 'converted' state
        Cart cart = getCartById(cartId);
        if (cart == null) {
            throw new IllegalStateException("Cart not found: " + cartId);
        }
        
        // Generate short ULID for order number
        String orderNumber = generateShortULID();
        order.setOrderNumber(orderNumber);
        
        // Snapshot cart lines into order lines
        List<Order.OrderLine> orderLines = new ArrayList<>();
        double itemsTotal = 0.0;
        
        for (Cart.CartLine cartLine : cart.getLines()) {
            Order.OrderLine orderLine = new Order.OrderLine();
            orderLine.setSku(cartLine.getSku());
            orderLine.setName(cartLine.getName());
            orderLine.setUnitPrice(cartLine.getPrice());
            orderLine.setQty(cartLine.getQty());
            orderLine.setLineTotal(cartLine.getPrice() * cartLine.getQty());
            orderLines.add(orderLine);
            
            itemsTotal += orderLine.getLineTotal();
            
            // Decrement product quantity
            decrementProductQuantity(cartLine.getSku(), cartLine.getQty());
        }
        
        order.setLines(orderLines);
        
        // Set order totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(itemsTotal);
        totals.setGrand(itemsTotal);
        order.setTotals(totals);
        
        // Copy guest contact from cart to order
        if (cart.getGuestContact() != null) {
            Order.GuestContact orderGuestContact = new Order.GuestContact();
            orderGuestContact.setName(cart.getGuestContact().getName());
            orderGuestContact.setEmail(cart.getGuestContact().getEmail());
            orderGuestContact.setPhone(cart.getGuestContact().getPhone());
            
            if (cart.getGuestContact().getAddress() != null) {
                Order.Address orderAddress = new Order.Address();
                orderAddress.setLine1(cart.getGuestContact().getAddress().getLine1());
                orderAddress.setCity(cart.getGuestContact().getAddress().getCity());
                orderAddress.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                orderAddress.setCountry(cart.getGuestContact().getAddress().getCountry());
                orderGuestContact.setAddress(orderAddress);
            }
            
            order.setGuestContact(orderGuestContact);
        }
        
        // Create shipment entity
        createShipment(order);
        
        // Update timestamps
        order.setUpdatedAt(Instant.now());
        
        logger.info("Order created successfully: {}", order.getOrderId());
        return order;
    }

    private String extractPaymentIdFromContext(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        // TODO: Extract from context - placeholder implementation
        return null;
    }

    private String extractCartIdFromContext(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        // TODO: Extract from context - placeholder implementation
        return null;
    }

    private Payment getPaymentById(String paymentId) {
        // TODO: Implement proper payment lookup
        return null;
    }

    private Cart getCartById(String cartId) {
        // TODO: Implement proper cart lookup
        return null;
    }

    private String generateShortULID() {
        // Simple implementation - in reality would use proper ULID library
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void decrementProductQuantity(String sku, Integer qty) {
        try {
            Product product = getProductBySku(sku);
            if (product != null) {
                product.setQuantityAvailable(product.getQuantityAvailable() - qty);
                // Update product with null transition (stays in same state)
                entityService.update(getProductEntityId(sku), product, null);
            }
        } catch (Exception e) {
            logger.error("Error decrementing product quantity for SKU: {}", sku, e);
        }
    }

    private Product getProductBySku(String sku) {
        try {
            Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("simple");
            condition.setConditions(java.util.List.of(skuCondition));
            
            Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
                Product.class, 
                Product.ENTITY_NAME, 
                Product.ENTITY_VERSION, 
                condition, 
                true
            );
            
            return productResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error fetching product by SKU: {}", sku, e);
            return null;
        }
    }

    private UUID getProductEntityId(String sku) {
        // TODO: Implement proper entity ID lookup
        return null;
    }

    private void createShipment(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId("ship_" + UUID.randomUUID().toString().substring(0, 8));
            shipment.setOrderId(order.getOrderId());
            
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
            shipment.setCreatedAt(Instant.now());
            shipment.setUpdatedAt(Instant.now());
            
            // Save shipment with create_shipment transition
            entityService.save(shipment);
            
        } catch (Exception e) {
            logger.error("Error creating shipment for order: {}", order.getOrderId(), e);
        }
    }
}
