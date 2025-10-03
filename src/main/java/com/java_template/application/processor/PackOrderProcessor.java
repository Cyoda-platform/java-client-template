package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.inventory_item.version_1.InventoryItem;
import com.java_template.application.entity.order.version_1.Order;
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
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
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
 * ABOUTME: PackOrderProcessor handles inventory reservation, stock deduction, and fulfillment task creation
 * during the transition from Paid to Packed state.
 */
@Component
public class PackOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PackOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PackOrderProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processPackOrder)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or Order entity is null");
            return false;
        }

        Order order = entityWithMetadata.entity();
        
        // Validate order is in correct state for packing
        String currentState = entityWithMetadata.metadata().getState();
        if (!"Paid".equals(currentState)) {
            logger.error("Order is not in Paid state for packing. Current state: {}, orderId: {}", 
                        currentState, order.getOrderId());
            return false;
        }

        // Validate payment is captured
        if (order.getPayment() == null || !"captured".equals(order.getPayment().getStatus())) {
            logger.error("Payment is not captured for orderId: {}", order.getOrderId());
            return false;
        }

        // Validate line items exist
        if (order.getLineItems() == null || order.getLineItems().isEmpty()) {
            logger.error("No line items found for orderId: {}", order.getOrderId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<Order> processPackOrder(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        logger.info("Processing packing for order with orderId: {}", order.getOrderId());

        try {
            // Reserve inventory for each line item
            for (Order.LineItem lineItem : order.getLineItems()) {
                reserveInventoryForLineItem(order.getOrderId(), lineItem);
                
                // Update fulfillment status to reserved
                lineItem.setFulfilmentStatus("reserved");
            }

            // Update all line items to packed status after successful reservation
            for (Order.LineItem lineItem : order.getLineItems()) {
                lineItem.setFulfilmentStatus("packed");
            }

            logger.info("Order packed successfully - orderId: {}, items: {}", 
                       order.getOrderId(), order.getLineItems().size());

            return new EntityWithMetadata<>(order, entityWithMetadata.metadata());

        } catch (Exception e) {
            logger.error("Error processing packing for orderId: {}", order.getOrderId(), e);
            
            // Revert fulfillment status on error
            if (order.getLineItems() != null) {
                order.getLineItems().forEach(item -> item.setFulfilmentStatus("pending"));
            }
            
            throw new RuntimeException("Failed to pack order: " + e.getMessage(), e);
        }
    }

    private void reserveInventoryForLineItem(String orderId, Order.LineItem lineItem) {
        try {
            logger.info("Reserving inventory for productId: {}, quantity: {}, orderId: {}", 
                       lineItem.getProductId(), lineItem.getQuantity(), orderId);

            // Find inventory item by productId
            ModelSpec inventoryModelSpec = new ModelSpec();
            inventoryModelSpec.setName(InventoryItem.ENTITY_NAME);
            inventoryModelSpec.setVersion(InventoryItem.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();
            SimpleCondition productCondition = new SimpleCondition()
                    .withJsonPath("$.productId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(lineItem.getProductId()));
            conditions.add(productCondition);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<InventoryItem>> inventoryItems = 
                entityService.search(inventoryModelSpec, groupCondition, InventoryItem.class);

            if (inventoryItems.isEmpty()) {
                throw new RuntimeException("Inventory item not found for productId: " + lineItem.getProductId());
            }

            EntityWithMetadata<InventoryItem> inventoryItemWithMetadata = inventoryItems.get(0);
            InventoryItem inventoryItem = inventoryItemWithMetadata.entity();

            // Check if sufficient stock is available
            Integer totalAvailable = inventoryItem.getTotalAvailableStock();
            if (totalAvailable < lineItem.getQuantity()) {
                throw new RuntimeException(String.format(
                    "Insufficient stock for productId: %s. Required: %d, Available: %d", 
                    lineItem.getProductId(), lineItem.getQuantity(), totalAvailable));
            }

            // Reserve stock (simplified - using first available location)
            String locationId = inventoryItem.getStockByLocation().keySet().iterator().next();
            InventoryItem.StockByLocation stock = inventoryItem.getStockByLocation().get(locationId);
            
            if (stock.getAvailable() >= lineItem.getQuantity()) {
                // Move from available to reserved
                stock.setAvailable(stock.getAvailable() - lineItem.getQuantity());
                stock.setReserved(stock.getReserved() + lineItem.getQuantity());
                stock.setLastUpdated(LocalDateTime.now());

                // Add audit log entry
                InventoryItem.AuditLogEntry auditEntry = new InventoryItem.AuditLogEntry();
                auditEntry.setEntryId(UUID.randomUUID().toString());
                auditEntry.setReason("order_reservation");
                auditEntry.setActor("PackOrderProcessor");
                auditEntry.setLocationId(locationId);
                auditEntry.setTimestamp(LocalDateTime.now());
                auditEntry.setReferenceId(orderId);
                auditEntry.setNotes("Reserved for order: " + orderId);

                InventoryItem.StockDelta delta = new InventoryItem.StockDelta();
                delta.setAvailableDelta(-lineItem.getQuantity());
                delta.setReservedDelta(lineItem.getQuantity());
                auditEntry.setDelta(delta);

                if (inventoryItem.getAuditLog() == null) {
                    inventoryItem.setAuditLog(new ArrayList<>());
                }
                inventoryItem.getAuditLog().add(auditEntry);

                // Update inventory item with manual transition
                entityService.save(inventoryItem, "reserve_inventory");
                
                logger.info("Reserved {} units of productId: {} at location: {} for orderId: {}", 
                           lineItem.getQuantity(), lineItem.getProductId(), locationId, orderId);
            } else {
                throw new RuntimeException(String.format(
                    "Insufficient stock at location %s for productId: %s. Required: %d, Available: %d", 
                    locationId, lineItem.getProductId(), lineItem.getQuantity(), stock.getAvailable()));
            }

        } catch (Exception e) {
            logger.error("Failed to reserve inventory for productId: {}, orderId: {}", 
                        lineItem.getProductId(), orderId, e);
            throw e;
        }
    }
}
