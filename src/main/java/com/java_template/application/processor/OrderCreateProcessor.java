package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderCreateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderCreateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order creation for request: {}", request.getId());

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
        return order != null;
    }

    private Order processOrderCreation(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();

        // Extract cart and payment references from request data - for now using hardcoded values
        // In a real implementation, this would come from the request payload
        String cartId = "cart_sample"; // TODO: Extract from request payload
        String paymentId = "pay_sample"; // TODO: Extract from request payload

        if (cartId == null || cartId.trim().isEmpty()) {
            throw new IllegalArgumentException("Cart ID is required for order creation");
        }
        if (paymentId == null || paymentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID is required for order creation");
        }

        logger.info("Creating order from cart: {} and payment: {}", cartId, paymentId);

        try {
            // Get cart and payment entities
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(Cart.class, cartId);
            Cart cart = cartResponse.getData();
            
            EntityResponse<Payment> paymentResponse = entityService.findByBusinessId(Payment.class, paymentId);
            Payment payment = paymentResponse.getData();

            if (cart == null) {
                throw new IllegalArgumentException("Cart not found for ID: " + cartId);
            }
            if (payment == null) {
                throw new IllegalArgumentException("Payment not found for ID: " + paymentId);
            }

            // Generate unique orderId and short ULID orderNumber
            if (order.getOrderId() == null || order.getOrderId().trim().isEmpty()) {
                order.setOrderId("order_" + UUID.randomUUID().toString().replace("-", ""));
            }
            if (order.getOrderNumber() == null || order.getOrderNumber().trim().isEmpty()) {
                order.setOrderNumber(UUID.randomUUID().toString().substring(0, 10).toUpperCase());
            }

            // Snapshot cart.lines to order.lines with lineTotal calculations
            List<Order.OrderLine> orderLines = new ArrayList<>();
            for (Cart.CartLine cartLine : cart.getLines()) {
                Order.OrderLine orderLine = new Order.OrderLine(
                    cartLine.getSku(),
                    cartLine.getName(),
                    cartLine.getPrice(),
                    cartLine.getQty()
                );
                orderLines.add(orderLine);
            }
            order.setLines(orderLines);

            // Copy cart.guestContact to order.guestContact
            if (cart.getGuestContact() != null) {
                Order.GuestContact orderGuestContact = copyGuestContact(cart.getGuestContact());
                order.setGuestContact(orderGuestContact);
            }

            // Calculate order.totals from lines
            order.recalculateTotals();

            // Decrement product stock for each order line
            for (Order.OrderLine orderLine : orderLines) {
                EntityResponse<Product> productResponse = entityService.findByBusinessId(Product.class, orderLine.getSku());
                Product product = productResponse.getData();
                
                if (product != null) {
                    int newQuantity = product.getQuantityAvailable() - orderLine.getQty();
                    if (newQuantity < 0) {
                        throw new IllegalStateException("Insufficient stock for product " + orderLine.getSku());
                    }
                    product.setQuantityAvailable(newQuantity);
                    entityService.updateByBusinessId(product, null);
                    logger.info("Updated stock for SKU {}: new quantity={}", orderLine.getSku(), newQuantity);
                }
            }

            // Set timestamps
            LocalDateTime now = LocalDateTime.now();
            if (order.getCreatedAt() == null) {
                order.setCreatedAt(now);
            }
            order.setUpdatedAt(now);

            // Create shipment with order reference
            createShipmentForOrder(order);

            logger.info("Order created successfully: orderId={}, orderNumber={}, totalItems={}, grandTotal={}", 
                order.getOrderId(), order.getOrderNumber(), order.getTotals().getItems(), order.getTotals().getGrand());

        } catch (Exception e) {
            logger.error("Failed to create order: {}", e.getMessage());
            throw new IllegalStateException("Failed to create order: " + e.getMessage());
        }
        
        return order;
    }

    private Order.GuestContact copyGuestContact(Cart.GuestContact cartGuestContact) {
        Order.GuestContact orderGuestContact = new Order.GuestContact();
        orderGuestContact.setName(cartGuestContact.getName());
        orderGuestContact.setEmail(cartGuestContact.getEmail());
        orderGuestContact.setPhone(cartGuestContact.getPhone());
        
        if (cartGuestContact.getAddress() != null) {
            Order.GuestAddress orderAddress = new Order.GuestAddress();
            orderAddress.setLine1(cartGuestContact.getAddress().getLine1());
            orderAddress.setCity(cartGuestContact.getAddress().getCity());
            orderAddress.setPostcode(cartGuestContact.getAddress().getPostcode());
            orderAddress.setCountry(cartGuestContact.getAddress().getCountry());
            orderGuestContact.setAddress(orderAddress);
        }
        
        return orderGuestContact;
    }

    private void createShipmentForOrder(Order order) {
        try {
            String shipmentId = "ship_" + UUID.randomUUID().toString().replace("-", "");
            
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            for (Order.OrderLine orderLine : order.getLines()) {
                Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine(orderLine.getSku(), orderLine.getQty());
                shipmentLines.add(shipmentLine);
            }
            
            Shipment shipment = new Shipment(shipmentId, order.getOrderId(), shipmentLines);
            entityService.save(shipment);
            
            logger.info("Shipment created for order: shipmentId={}, orderId={}", shipmentId, order.getOrderId());
        } catch (Exception e) {
            logger.error("Failed to create shipment for order {}: {}", order.getOrderId(), e.getMessage());
            // Don't fail order creation if shipment creation fails
        }
    }
}
