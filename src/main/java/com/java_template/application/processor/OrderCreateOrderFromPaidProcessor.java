package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class OrderCreateOrderFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreateOrderFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderCreateOrderFromPaidProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
            .map(this::processOrderLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidOrder(Order order) {
        return order != null;
    }

    private Order processOrderLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();

        try {
            // Get paymentId and cartId from the order entity itself
            String paymentId = order.getPaymentId();
            String cartId = order.getCartId();

            if (paymentId == null || cartId == null) {
                throw new RuntimeException("Both paymentId and cartId are required");
            }

            // Validate payment exists and is PAID
            EntityResponse<Payment> paymentResponse = entityService.findByBusinessId(
                Payment.class,
                paymentId,
                "paymentId"
            );

            if (paymentResponse == null) {
                throw new RuntimeException("Payment not found with ID: " + paymentId);
            }

            Payment payment = paymentResponse.getData();
            String paymentState = paymentResponse.getMetadata().getState();

            if (!"paid".equals(paymentState)) {
                throw new RuntimeException("Payment must be PAID, current state: " + paymentState);
            }

            // Validate cart exists and is CONVERTED
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(
                Cart.class,
                cartId,
                "cartId"
            );

            if (cartResponse == null) {
                throw new RuntimeException("Cart not found with ID: " + cartId);
            }

            Cart cart = cartResponse.getData();
            String cartState = cartResponse.getMetadata().getState();

            if (!"CONVERTED".equals(cartState)) {
                throw new RuntimeException("Cart must be CONVERTED, current state: " + cartState);
            }

            // Validate payment and cart match
            if (!payment.getCartId().equals(cartId)) {
                throw new RuntimeException("Payment and cart must match");
            }

            // Generate order number (short ULID)
            order.setOrderNumber(generateShortUlid());

            // Generate order ID if not set
            if (order.getOrderId() == null || order.getOrderId().trim().isEmpty()) {
                order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }

            // Snapshot cart lines to order lines and decrement stock
            List<Order.OrderLine> orderLines = new ArrayList<>();
            for (Cart.CartLine cartLine : cart.getLines()) {
                // Create order line
                Order.OrderLine orderLine = new Order.OrderLine();
                orderLine.setSku(cartLine.getSku());
                orderLine.setName(cartLine.getName());
                orderLine.setUnitPrice(cartLine.getPrice());
                orderLine.setQty(cartLine.getQty());
                orderLine.setLineTotal(cartLine.getPrice() * cartLine.getQty());
                orderLines.add(orderLine);

                // Decrement product stock
                decrementProductStock(cartLine.getSku(), cartLine.getQty());
            }
            order.setLines(orderLines);

            // Set order totals
            Order.OrderTotals totals = new Order.OrderTotals();
            totals.setItems(cart.getTotalItems());
            totals.setGrand(cart.getGrandTotal());
            order.setTotals(totals);

            // Snapshot guest contact
            order.setGuestContact(copyGuestContact(cart.getGuestContact()));

            // Create shipment
            createShipment(order);

            logger.info("Order created successfully with ID: {} and number: {}", order.getOrderId(), order.getOrderNumber());
            return order;

        } catch (Exception e) {
            logger.error("Error processing order creation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create order from paid payment: " + e.getMessage(), e);
        }
    }

    private void decrementProductStock(String sku, Integer qty) {
        try {
            EntityResponse<Product> productResponse = entityService.findByBusinessId(
                Product.class,
                sku,
                "sku"
            );

            if (productResponse == null) {
                throw new RuntimeException("Product not found for SKU: " + sku);
            }

            Product product = productResponse.getData();
            UUID productId = productResponse.getMetadata().getId();

            if (product.getQuantityAvailable() < qty) {
                throw new RuntimeException("Insufficient stock for " + sku + 
                    ". Available: " + product.getQuantityAvailable() + ", Required: " + qty);
            }

            product.setQuantityAvailable(product.getQuantityAvailable() - qty);
            entityService.update(productId, product, null);

            logger.info("Decremented stock for SKU {}: {} units", sku, qty);

        } catch (Exception e) {
            logger.error("Error decrementing stock for SKU {}: {}", sku, e.getMessage(), e);
            throw new RuntimeException("Failed to decrement stock: " + e.getMessage(), e);
        }
    }

    private Order.GuestContact copyGuestContact(Cart.GuestContact cartContact) {
        if (cartContact == null) {
            throw new RuntimeException("Guest contact is required");
        }

        Order.GuestContact orderContact = new Order.GuestContact();
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

        return orderContact;
    }

    private void createShipment(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId("SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
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

            entityService.save(shipment);
            logger.info("Shipment created with ID: {}", shipment.getShipmentId());

        } catch (Exception e) {
            logger.error("Error creating shipment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create shipment: " + e.getMessage(), e);
        }
    }

    private String generateShortUlid() {
        // Simple implementation - in real scenario would use proper ULID library
        return "01J" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
