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
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
public class OrderCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderCreationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
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

        // Note: In a real implementation, cartId and paymentId would come from the request payload
        // For this demo, we'll assume they are somehow available in the order entity or request
        
        // Extract cartId and paymentId from order or request - this is a simplified approach
        String cartId = extractCartIdFromRequest(context);
        String paymentId = extractPaymentIdFromRequest(context);

        // Validate payment is in PAID state
        Optional<EntityResponse<Payment>> paymentResponse = getPaymentById(paymentId);
        if (paymentResponse.isEmpty()) {
            throw new IllegalArgumentException("Payment not found: " + paymentId);
        }

        String paymentState = paymentResponse.get().getMetadata().getState();
        if (!"PAID".equals(paymentState)) {
            throw new IllegalArgumentException("Payment must be in PAID state, current state: " + paymentState);
        }

        // Get cart entity by cartId
        Optional<EntityResponse<Cart>> cartResponse = getCartById(cartId);
        if (cartResponse.isEmpty()) {
            throw new IllegalArgumentException("Cart not found: " + cartId);
        }

        Cart cart = cartResponse.get().getData();
        String cartState = cartResponse.get().getMetadata().getState();
        if (!"CHECKING_OUT".equals(cartState)) {
            throw new IllegalArgumentException("Cart must be in CHECKING_OUT state, current state: " + cartState);
        }

        // Generate unique orderId and short ULID orderNumber
        if (order.getOrderId() == null || order.getOrderId().trim().isEmpty()) {
            order.setOrderId("order-" + UUID.randomUUID().toString());
        }
        if (order.getOrderNumber() == null || order.getOrderNumber().trim().isEmpty()) {
            order.setOrderNumber(generateShortULID());
        }

        // Process cart lines and update product stock
        List<Order.OrderLine> orderLines = new ArrayList<>();
        int totalItems = 0;
        double grandTotal = 0.0;

        for (Cart.CartLine cartLine : cart.getLines()) {
            // Get product by sku
            Optional<EntityResponse<Product>> productResponse = getProductBySku(cartLine.getSku());
            if (productResponse.isEmpty()) {
                throw new IllegalArgumentException("Product not found: " + cartLine.getSku());
            }

            Product product = productResponse.get().getData();
            String productState = productResponse.get().getMetadata().getState();
            UUID productId = productResponse.get().getMetadata().getId();

            if (!"ACTIVE".equals(productState)) {
                throw new IllegalArgumentException("Product not active: " + cartLine.getSku());
            }

            if (product.getQuantityAvailable() < cartLine.getQty()) {
                throw new IllegalArgumentException("Insufficient stock for product: " + cartLine.getSku());
            }

            // Decrement product.quantityAvailable by line.qty
            product.setQuantityAvailable(product.getQuantityAvailable() - cartLine.getQty());
            
            // Update product entity (no transition - just update the data)
            entityService.update(productId, product, null);

            // Create order line with snapshot data
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

        // Copy cart.guestContact to order.guestContact
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

        // Calculate order totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(totalItems);
        totals.setGrand(grandTotal);
        order.setTotals(totals);

        // Set timestamps
        Instant now = Instant.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        // Create shipment entity
        createShipment(order);

        // Trigger cart CHECKOUT transition
        UUID cartEntityId = cartResponse.get().getMetadata().getId();
        entityService.update(cartEntityId, cart, "CHECKOUT");

        logger.info("Order {} created successfully", order.getOrderId());
        return order;
    }

    private String extractCartIdFromRequest(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        // In a real implementation, this would extract from the request payload
        // For demo purposes, we'll return a placeholder
        return "cart-placeholder";
    }

    private String extractPaymentIdFromRequest(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        // In a real implementation, this would extract from the request payload
        // For demo purposes, we'll return a placeholder
        return "pay-placeholder";
    }

    private Optional<EntityResponse<Payment>> getPaymentById(String paymentId) {
        Condition condition = Condition.of("$.paymentId", "EQUALS", paymentId);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
        return entityService.getFirstItemByCondition(Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION, searchCondition, true);
    }

    private Optional<EntityResponse<Cart>> getCartById(String cartId) {
        Condition condition = Condition.of("$.cartId", "EQUALS", cartId);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
        return entityService.getFirstItemByCondition(Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, searchCondition, true);
    }

    private Optional<EntityResponse<Product>> getProductBySku(String sku) {
        Condition condition = Condition.of("$.sku", "EQUALS", sku);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
        return entityService.getFirstItemByCondition(Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, searchCondition, true);
    }

    private String generateShortULID() {
        // Simple ULID-like generation for demo purposes
        return "01" + System.currentTimeMillis() + "ABC";
    }

    private void createShipment(Order order) {
        Shipment shipment = new Shipment();
        shipment.setShipmentId("ship-" + UUID.randomUUID().toString());
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
        
        Instant now = Instant.now();
        shipment.setCreatedAt(now);
        shipment.setUpdatedAt(now);
        
        // Save shipment with CREATE_SHIPMENT transition
        EntityResponse<Shipment> shipmentResponse = entityService.save(shipment);
        UUID shipmentId = shipmentResponse.getMetadata().getId();
        entityService.update(shipmentId, shipment, "CREATE_SHIPMENT");
        
        logger.info("Shipment {} created for order {}", shipment.getShipmentId(), order.getOrderId());
    }
}
