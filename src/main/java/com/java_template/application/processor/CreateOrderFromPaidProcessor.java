package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CreateOrderFromPaidProcessor - Creates order from paid cart and decrements stock
 * 
 * This processor handles the complex order creation workflow:
 * 1. Validates payment is PAID
 * 2. Snapshots cart data into order
 * 3. Decrements product stock
 * 4. Creates shipment in PICKING state
 */
@Component
public class CreateOrderFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOrderFromPaidProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order creation from paid cart for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::createOrderFromPaid)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && technicalId != null;
    }

    /**
     * Main order creation logic
     * 
     * CRITICAL: This processor creates the order from cart data, decrements stock,
     * and creates a shipment. It does NOT update the current order entity state.
     */
    private EntityWithMetadata<Order> createOrderFromPaid(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Creating order from paid cart. Order: {} in state: {}", order.getOrderId(), currentState);

        try {
            // Find the payment for this order (assuming orderId matches paymentId for simplicity)
            Payment payment = findPaymentByOrderId(order.getOrderId());
            if (payment == null) {
                logger.error("Payment not found for order: {}", order.getOrderId());
                throw new RuntimeException("Payment not found for order");
            }

            // Validate payment is PAID
            EntityWithMetadata<Payment> paymentWithMetadata = getPaymentWithMetadata(payment.getPaymentId());
            if (!"paid".equals(paymentWithMetadata.metadata().getState())) {
                logger.error("Payment {} is not in PAID state: {}", payment.getPaymentId(), paymentWithMetadata.metadata().getState());
                throw new RuntimeException("Payment is not in PAID state");
            }

            // Find the cart
            Cart cart = findCartById(payment.getCartId());
            if (cart == null) {
                logger.error("Cart not found: {}", payment.getCartId());
                throw new RuntimeException("Cart not found");
            }

            // Snapshot cart data into order
            snapshotCartToOrder(cart, order);

            // Decrement product stock
            decrementProductStock(order.getLines());

            // Create shipment
            createShipmentForOrder(order);

            logger.info("Order {} created successfully from cart {} with {} items", 
                       order.getOrderId(), payment.getCartId(), order.getTotals().getItems());

        } catch (Exception e) {
            logger.error("Error creating order from paid cart: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create order from paid cart", e);
        }

        return entityWithMetadata;
    }

    /**
     * Find payment by order ID (simplified lookup)
     */
    private Payment findPaymentByOrderId(String orderId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            EntityWithMetadata<Payment> paymentResponse = entityService.findByBusinessId(
                    modelSpec, orderId, "paymentId", Payment.class);
            return paymentResponse != null ? paymentResponse.entity() : null;
        } catch (Exception e) {
            logger.error("Error finding payment by order ID: {}", orderId, e);
            return null;
        }
    }

    /**
     * Get payment with metadata
     */
    private EntityWithMetadata<Payment> getPaymentWithMetadata(String paymentId) {
        ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
        return entityService.findByBusinessId(modelSpec, paymentId, "paymentId", Payment.class);
    }

    /**
     * Find cart by ID
     */
    private Cart findCartById(String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    modelSpec, cartId, "cartId", Cart.class);
            return cartResponse != null ? cartResponse.entity() : null;
        } catch (Exception e) {
            logger.error("Error finding cart by ID: {}", cartId, e);
            return null;
        }
    }

    /**
     * Snapshot cart data into order
     */
    private void snapshotCartToOrder(Cart cart, Order order) {
        // Generate short ULID for order number (simplified)
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        // Convert cart lines to order lines
        List<Order.OrderLine> orderLines = new ArrayList<>();
        for (Cart.CartLine cartLine : cart.getLines()) {
            Order.OrderLine orderLine = new Order.OrderLine();
            orderLine.setSku(cartLine.getSku());
            orderLine.setName(cartLine.getName());
            orderLine.setUnitPrice(cartLine.getPrice());
            orderLine.setQty(cartLine.getQty());
            orderLine.setLineTotal(cartLine.getLineTotal());
            orderLines.add(orderLine);
        }
        order.setLines(orderLines);

        // Set totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(cart.getTotalItems());
        totals.setGrand(cart.getGrandTotal());
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

        order.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Decrement product stock for ordered items
     */
    private void decrementProductStock(List<Order.OrderLine> orderLines) {
        for (Order.OrderLine orderLine : orderLines) {
            try {
                ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
                EntityWithMetadata<Product> productResponse = entityService.findByBusinessId(
                        productModelSpec, orderLine.getSku(), "sku", Product.class);

                if (productResponse != null) {
                    Product product = productResponse.entity();
                    int currentStock = product.getQuantityAvailable();
                    int newStock = Math.max(0, currentStock - orderLine.getQty());
                    product.setQuantityAvailable(newStock);

                    // Update product with stock update transition
                    entityService.update(productResponse.metadata().getId(), product, "UPDATE_STOCK");
                    
                    logger.info("Stock decremented for product {}: {} -> {} (ordered: {})", 
                               orderLine.getSku(), currentStock, newStock, orderLine.getQty());
                } else {
                    logger.warn("Product not found for stock decrement: {}", orderLine.getSku());
                }
            } catch (Exception e) {
                logger.error("Error decrementing stock for product: {}", orderLine.getSku(), e);
            }
        }
    }

    /**
     * Create shipment for the order
     */
    private void createShipmentForOrder(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
            shipment.setOrderId(order.getOrderId());

            // Convert order lines to shipment lines
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

            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());

            // Create shipment (will start in PICKING state)
            entityService.create(shipment);
            
            logger.info("Shipment {} created for order {}", shipment.getShipmentId(), order.getOrderId());
        } catch (Exception e) {
            logger.error("Error creating shipment for order: {}", order.getOrderId(), e);
        }
    }
}
