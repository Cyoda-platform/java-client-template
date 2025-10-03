package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.inventory_item.version_1.InventoryItem;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Criterion to check inventory availability for order line items
 * Validates that sufficient stock is available for all products
 */
@Component
public class InventoryAvailabilityCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(InventoryAvailabilityCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public InventoryAvailabilityCriterion(SerializerFactory serializerFactory, 
                                        EntityService entityService, 
                                        ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.info("Checking {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntity(Order.class)
                .validate(this::isValidOrder, "Invalid order")
                .map(this::checkInventoryAvailability)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidOrder(Order order) {
        return order != null && order.getOrderId() != null && 
               order.getLineItems() != null && !order.getLineItems().isEmpty();
    }

    private boolean checkInventoryAvailability(CriterionSerializer.CriterionEntityResponseExecutionContext<Order> context) {
        Order order = context.entity();
        
        logger.debug("Checking inventory availability for order: {}", order.getOrderId());

        try {
            // Check availability for each line item
            for (Order.LineItem lineItem : order.getLineItems()) {
                if (!checkLineItemAvailability(order, lineItem)) {
                    logger.warn("Insufficient inventory for product {} in order {}", 
                               lineItem.getProductId(), order.getOrderId());
                    return false;
                }
            }

            logger.info("Inventory availability check passed for order: {}", order.getOrderId());
            return true;

        } catch (Exception e) {
            logger.error("Error during inventory availability check for order: {}", order.getOrderId(), e);
            return false;
        }
    }

    private boolean checkLineItemAvailability(Order order, Order.LineItem lineItem) {
        try {
            // Find inventory item for this product
            InventoryItem inventoryItem = findInventoryItemByProductId(lineItem.getProductId());
            
            if (inventoryItem == null) {
                logger.debug("Inventory item not found for product: {}", lineItem.getProductId());
                return false;
            }

            // Check if product is expired
            if (inventoryItem.isExpired()) {
                logger.debug("Product {} is expired", lineItem.getProductId());
                return false;
            }

            // Check available stock
            int availableStock = inventoryItem.getTotalAvailableStock();
            int requiredQuantity = lineItem.getQuantity();

            if (availableStock < requiredQuantity) {
                logger.debug("Insufficient stock for product {} - Available: {}, Required: {}", 
                           lineItem.getProductId(), availableStock, requiredQuantity);
                return false;
            }

            // Check if any location has sufficient stock
            if (!hasLocationWithSufficientStock(inventoryItem, requiredQuantity)) {
                logger.debug("No single location has sufficient stock for product {} - Required: {}", 
                           lineItem.getProductId(), requiredQuantity);
                return false;
            }

            logger.debug("Stock availability confirmed for product {} - Available: {}, Required: {}", 
                        lineItem.getProductId(), availableStock, requiredQuantity);
            return true;

        } catch (Exception e) {
            logger.error("Error checking availability for product: {}", lineItem.getProductId(), e);
            return false;
        }
    }

    private InventoryItem findInventoryItemByProductId(String productId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(InventoryItem.ENTITY_NAME)
                    .withVersion(InventoryItem.ENTITY_VERSION);

            EntityWithMetadata<InventoryItem> inventoryItemWithMetadata = entityService.findByBusinessId(
                    modelSpec, productId, "productId", InventoryItem.class);

            return inventoryItemWithMetadata != null ? inventoryItemWithMetadata.entity() : null;

        } catch (Exception e) {
            logger.error("Error finding inventory item for product: {}", productId, e);
            return null;
        }
    }

    private boolean hasLocationWithSufficientStock(InventoryItem inventoryItem, int requiredQuantity) {
        if (inventoryItem.getStockByLocation() == null) {
            return false;
        }

        return inventoryItem.getStockByLocation().stream()
                .anyMatch(stock -> stock.getAvailable() != null && 
                                 stock.getAvailable() >= requiredQuantity);
    }

    /**
     * Additional validation for stock quality and conditions
     */
    private boolean validateStockQuality(InventoryItem inventoryItem) {
        // Check if item is approaching expiry (within 7 days)
        if (inventoryItem.getAttributes() != null && 
            inventoryItem.getAttributes().getExpiryDate() != null) {
            
            java.time.LocalDateTime warningDate = java.time.LocalDateTime.now().plusDays(7);
            if (inventoryItem.getAttributes().getExpiryDate().isBefore(warningDate)) {
                logger.warn("Product {} is approaching expiry: {}", 
                           inventoryItem.getProductId(), 
                           inventoryItem.getAttributes().getExpiryDate());
                // Still allow sale but log warning
            }
        }

        // Check for excessive damaged stock
        if (inventoryItem.getTotalDamaged() != null && inventoryItem.getTotalDamaged() > 0) {
            int totalStock = inventoryItem.getTotalAvailableStock() + 
                           inventoryItem.getTotalReservedStock() + 
                           inventoryItem.getTotalDamaged();
            
            if (totalStock > 0) {
                double damagedPercentage = (double) inventoryItem.getTotalDamaged() / totalStock * 100;
                if (damagedPercentage > 20) { // More than 20% damaged
                    logger.warn("High damaged stock percentage for product {}: {}%", 
                               inventoryItem.getProductId(), 
                               String.format("%.1f", damagedPercentage));
                }
            }
        }

        return true;
    }

    /**
     * Check if product is available for specific channel
     */
    private boolean isProductAvailableForChannel(InventoryItem inventoryItem, String channel) {
        // In a real system, products might have channel-specific availability
        // For now, all products are available for all channels
        return true;
    }

    /**
     * Calculate lead time for product availability
     */
    private int calculateLeadTime(InventoryItem inventoryItem, int requiredQuantity) {
        int availableStock = inventoryItem.getTotalAvailableStock();
        
        if (availableStock >= requiredQuantity) {
            return 0; // Immediate availability
        }
        
        // Check if reorder is possible
        if (inventoryItem.getReorderQuantity() != null && 
            inventoryItem.getSupplierRef() != null) {
            return 7; // Assume 7 days lead time for reorder
        }
        
        return -1; // Not available
    }
}
