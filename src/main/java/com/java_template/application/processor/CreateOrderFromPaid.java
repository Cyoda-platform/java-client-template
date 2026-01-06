package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Processor that creates an order from a paid payment, including:
 * - Snapshotting cart lines and guest contact into Order
 * - Decrementing Product.quantityAvailable for each ordered item
 * - Creating a single Shipment in PICKING state
 */
@Component
public class CreateOrderFromPaid implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaid.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOrderFromPaid(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::processBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Order> processBusinessLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid payment: {}", order.getOrderId());

        // Decrement product quantities for each order line
        if (order.getLines() != null && !order.getLines().isEmpty()) {
            for (Order.OrderLine line : order.getLines()) {
                decrementProductQuantity(line.getSku(), line.getQty());
            }
        }

        // Create shipment for this order
        createShipmentForOrder(order);

        order.setUpdatedAt(LocalDateTime.now());

        logger.info("Order {} created from paid payment with {} lines", 
            order.getOrderId(), order.getLines() != null ? order.getLines().size() : 0);

        return entityWithMetadata;
    }

    private void decrementProductQuantity(String sku, Integer qty) {
        try {
            ModelSpec productModelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);

            // Find product by SKU
            SimpleCondition skuCondition = new SimpleCondition()
                    .withJsonPath("$.sku")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(sku));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(skuCondition));

            List<EntityWithMetadata<Product>> products = entityService.search(productModelSpec, condition, Product.class);

            if (!products.isEmpty()) {
                EntityWithMetadata<Product> productWithMetadata = products.get(0);
                Product product = productWithMetadata.entity();
                UUID productId = productWithMetadata.metadata().getId();

                // Decrement quantity
                if (product.getQuantityAvailable() != null && qty != null) {
                    product.setQuantityAvailable(Math.max(0, product.getQuantityAvailable() - qty));
                    entityService.update(productId, product, null);
                    logger.debug("Decremented product {} quantity by {}", sku, qty);
                }
            } else {
                logger.warn("Product with SKU {} not found", sku);
            }
        } catch (Exception e) {
            logger.error("Error decrementing product quantity for SKU {}: {}", sku, e.getMessage());
        }
    }

    private void createShipmentForOrder(Order order) {
        try {
            // Create shipment lines from order lines
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            if (order.getLines() != null) {
                for (Order.OrderLine orderLine : order.getLines()) {
                    Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
                    shipmentLine.setSku(orderLine.getSku());
                    shipmentLine.setQtyOrdered(orderLine.getQty());
                    shipmentLine.setQtyPicked(0);
                    shipmentLine.setQtyShipped(0);
                    shipmentLines.add(shipmentLine);
                }
            }

            // Create shipment entity
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
            shipment.setOrderId(order.getOrderId());
            shipment.setLines(shipmentLines);
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());

            // Save shipment
            entityService.create(shipment);
            logger.info("Created shipment {} for order {}", shipment.getShipmentId(), order.getOrderId());
        } catch (Exception e) {
            logger.error("Error creating shipment for order {}: {}", order.getOrderId(), e.getMessage());
        }
    }
}

