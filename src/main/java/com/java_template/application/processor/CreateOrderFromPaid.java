package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.util.UlidGenerator;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
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
 * Processor for creating order from paid payment
 * Snapshots cart data, decrements product stock, and creates shipment
 */
@Component
public class CreateOrderFromPaid implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaid.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateOrderFromPaid(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::createOrderFromPaidWithContext)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validate the entity wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or entity is null");
            return false;
        }

        Order order = entityWithMetadata.entity();
        if (!order.isValid()) {
            logger.error("Order entity is not valid: {}", order);
            return false;
        }

        return true;
    }

    /**
     * Create order from paid payment with context
     */
    private EntityWithMetadata<Order> createOrderFromPaidWithContext(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();
        logger.info("Creating order from paid payment: {}", order.getOrderId());

        try {
            // Generate short ULID for order number
            String orderNumber = UlidGenerator.generateShortUlid();
            order.setOrderNumber(orderNumber);
            order.setStatus("WAITING_TO_FULFILL");
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // Decrement product stock for each order line
            decrementProductStock(order);

            // Create shipment for this order
            createShipmentForOrder(order);

            logger.info("Created order {} with order number {} from paid payment", 
                       order.getOrderId(), orderNumber);
            
            return entityWithMetadata;

        } catch (Exception e) {
            logger.error("Error creating order from paid payment: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create order from paid payment", e);
        }
    }

    /**
     * Decrement product stock for each order line
     */
    private void decrementProductStock(Order order) {
        logger.info("Decrementing product stock for order: {}", order.getOrderId());

        ModelSpec productModelSpec = new ModelSpec();
        productModelSpec.setName(Product.ENTITY_NAME);
        productModelSpec.setVersion(Product.ENTITY_VERSION);

        for (Order.OrderLine orderLine : order.getLines()) {
            try {
                // Find product by SKU
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                    productModelSpec, orderLine.getSku(), "sku", Product.class);

                if (productWithMetadata != null) {
                    Product product = productWithMetadata.entity();
                    int currentStock = product.getQuantityAvailable();
                    int newStock = Math.max(0, currentStock - orderLine.getQty());
                    
                    product.setQuantityAvailable(newStock);
                    
                    // Update product with inventory transition
                    entityService.updateByBusinessId(product, "sku", "update_inventory");
                    
                    logger.info("Decremented stock for SKU {} from {} to {} (ordered: {})", 
                               orderLine.getSku(), currentStock, newStock, orderLine.getQty());
                } else {
                    logger.warn("Product not found for SKU: {} in order: {}", orderLine.getSku(), order.getOrderId());
                }
            } catch (Exception e) {
                logger.error("Error decrementing stock for SKU: {} in order: {}", orderLine.getSku(), order.getOrderId(), e);
                // Continue processing other items even if one fails
            }
        }
    }

    /**
     * Create shipment for the order
     */
    private void createShipmentForOrder(Order order) {
        logger.info("Creating shipment for order: {}", order.getOrderId());

        try {
            // Generate unique shipment ID
            String shipmentId = "SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            
            Shipment shipment = new Shipment();
            shipment.setShipmentId(shipmentId);
            shipment.setOrderId(order.getOrderId());
            shipment.setStatus("PICKING");
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());

            // Create shipment lines from order lines
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

            // Create shipment entity
            EntityWithMetadata<Shipment> savedShipment = entityService.create(shipment);
            
            logger.info("Created shipment {} for order {}", shipmentId, order.getOrderId());

        } catch (Exception e) {
            logger.error("Error creating shipment for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create shipment for order", e);
        }
    }
}
