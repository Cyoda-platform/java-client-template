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
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid order state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        
        logger.info("Creating order from paid payment for order: {}", order.getOrderId());

        // Validate order entity
        if (order == null) {
            logger.error("Order entity is null");
            throw new IllegalArgumentException("Order entity cannot be null");
        }

        // Generate IDs if not set
        if (order.getOrderId() == null || order.getOrderId().trim().isEmpty()) {
            String orderId = "ord_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            order.setOrderId(orderId);
        }

        if (order.getOrderNumber() == null || order.getOrderNumber().trim().isEmpty()) {
            String orderNumber = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            order.setOrderNumber(orderNumber);
        }

        // Set timestamps
        if (order.getCreatedAt() == null) {
            order.setCreatedAt(LocalDateTime.now());
        }
        order.setUpdatedAt(LocalDateTime.now());

        // Process inventory updates for each order line
        if (order.getLines() != null) {
            for (Order.OrderLine orderLine : order.getLines()) {
                updateProductInventory(orderLine.getSku(), orderLine.getQty());
            }
        }

        // Create associated shipment
        createShipmentForOrder(order);

        logger.info("Order created successfully: {} with {} lines", 
                   order.getOrderId(), order.getLines() != null ? order.getLines().size() : 0);

        return order;
    }

    /**
     * Update product inventory by decrementing available quantity.
     */
    private void updateProductInventory(String sku, Integer orderedQty) {
        try {
            logger.info("Updating inventory for product: {}, ordered quantity: {}", sku, orderedQty);

            // Find product by SKU
            SearchConditionRequest condition = SearchConditionRequest.group("and",
                Condition.of("sku", "equals", sku));
            
            var productResponse = entityService.getFirstItemByCondition(Product.class, condition, false);
            
            if (productResponse.isPresent()) {
                Product product = productResponse.get().getData();
                UUID entityId = productResponse.get().getMetadata().getId();
                
                // Check if sufficient stock is available
                if (product.getQuantityAvailable() < orderedQty) {
                    logger.error("Insufficient stock for product {}: available={}, ordered={}", 
                               sku, product.getQuantityAvailable(), orderedQty);
                    throw new IllegalStateException("Insufficient stock for product: " + sku);
                }
                
                // Decrement available quantity
                int newQuantity = product.getQuantityAvailable() - orderedQty;
                product.setQuantityAvailable(newQuantity);
                
                // Update product
                entityService.update(entityId, product, null);
                
                logger.info("Product inventory updated: {} - new quantity: {}", sku, newQuantity);
                
            } else {
                logger.error("Product not found for inventory update: {}", sku);
                throw new IllegalStateException("Product not found: " + sku);
            }
            
        } catch (Exception e) {
            logger.error("Error updating product inventory for SKU: {}", sku, e);
            throw new RuntimeException("Failed to update product inventory", e);
        }
    }

    /**
     * Create shipment entity for the order.
     */
    private void createShipmentForOrder(Order order) {
        try {
            logger.info("Creating shipment for order: {}", order.getOrderId());

            // Generate shipment ID
            String shipmentId = "ship_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            
            // Create shipment lines from order lines
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            if (order.getLines() != null) {
                shipmentLines = order.getLines().stream()
                    .map(orderLine -> new Shipment.ShipmentLine(
                        orderLine.getSku(),
                        orderLine.getQty(),
                        0, // qtyPicked starts at 0
                        0  // qtyShipped starts at 0
                    ))
                    .collect(Collectors.toList());
            }
            
            // Create shipment entity
            Shipment shipment = new Shipment();
            shipment.setShipmentId(shipmentId);
            shipment.setOrderId(order.getOrderId());
            shipment.setLines(shipmentLines);
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());
            
            // Save shipment with CREATE_SHIPMENT transition
            entityService.save(shipment);
            
            logger.info("Shipment created successfully: {} for order: {}", shipmentId, order.getOrderId());
            
        } catch (Exception e) {
            logger.error("Error creating shipment for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create shipment", e);
        }
    }
}
