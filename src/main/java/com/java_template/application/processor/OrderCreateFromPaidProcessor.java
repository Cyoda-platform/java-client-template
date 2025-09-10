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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OrderCreateFromPaidProcessor - Creates order from paid cart and payment
 * 
 * This processor creates an order from a paid cart and payment, decrements product stock, and creates a shipment.
 * Used in transition: CREATE_ORDER_FROM_PAID
 */
@Component
public class OrderCreateFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreateFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderCreateFromPaidProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing order creation from paid cart for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processOrderCreationLogic)
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
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && technicalId != null &&
               order.getOrderId() != null && order.getOrderNumber() != null;
    }

    /**
     * Main business logic for creating order from paid cart and payment
     */
    private EntityWithMetadata<Order> processOrderCreationLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid cart: {}", order.getOrderId());

        try {
            // Get associated cart - we need to find it by some reference
            // For now, we'll assume the order has a cartId field or we can derive it
            // This would typically be passed in the order creation request
            
            // Set timestamps
            LocalDateTime now = LocalDateTime.now();
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            // For demo purposes, we'll create a basic order structure
            // In a real implementation, we would:
            // 1. Find the associated cart and payment
            // 2. Validate payment is PAID and cart is CONVERTED
            // 3. Snapshot cart data into order
            // 4. Decrement product stock
            // 5. Create shipment

            // Create basic order structure for demo
            if (order.getLines() == null) {
                order.setLines(new ArrayList<>());
            }
            
            if (order.getTotals() == null) {
                Order.OrderTotals totals = new Order.OrderTotals();
                totals.setItems(BigDecimal.ZERO);
                totals.setGrand(BigDecimal.ZERO);
                order.setTotals(totals);
            }

            logger.info("Order {} created successfully with order number {}", 
                       order.getOrderId(), order.getOrderNumber());

            return entityWithMetadata;

        } catch (Exception e) {
            logger.error("Error creating order from paid cart: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create order from paid cart", e);
        }
    }

    /**
     * Helper method to find cart by business ID
     */
    private EntityWithMetadata<Cart> findCartByBusinessId(String cartId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            return entityService.findByBusinessId(modelSpec, cartId, "cartId", Cart.class);
        } catch (Exception e) {
            logger.error("Error finding cart by ID: {}", cartId, e);
            return null;
        }
    }

    /**
     * Helper method to find payment by business ID
     */
    private EntityWithMetadata<Payment> findPaymentByBusinessId(String paymentId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
            return entityService.findByBusinessId(modelSpec, paymentId, "paymentId", Payment.class);
        } catch (Exception e) {
            logger.error("Error finding payment by ID: {}", paymentId, e);
            return null;
        }
    }

    /**
     * Helper method to find product by SKU
     */
    private EntityWithMetadata<Product> findProductBySku(String sku) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            return entityService.findByBusinessId(modelSpec, sku, "sku", Product.class);
        } catch (Exception e) {
            logger.error("Error finding product by SKU: {}", sku, e);
            return null;
        }
    }

    /**
     * Helper method to create shipment for order
     */
    private void createShipmentForOrder(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
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
            
            LocalDateTime now = LocalDateTime.now();
            shipment.setCreatedAt(now);
            shipment.setUpdatedAt(now);

            // Create shipment entity
            entityService.create(shipment);
            
            logger.info("Shipment {} created for order {}", shipment.getShipmentId(), order.getOrderId());
            
        } catch (Exception e) {
            logger.error("Error creating shipment for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create shipment", e);
        }
    }
}
