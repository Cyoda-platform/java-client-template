package com.java_template.application.criterion;

import com.java_template.application.entity.inventory_item.version_1.InventoryItem;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: StockAvailabilityCriterion validates inventory stock levels and prevents
 * negative stock situations during stock adjustments and reservations.
 */
@Component
public class StockAvailabilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public StockAvailabilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Stock availability criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(InventoryItem.class, this::validateStockAvailability)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateStockAvailability(CriterionSerializer.CriterionEntityEvaluationContext<InventoryItem> context) {
        InventoryItem inventoryItem = context.entityWithMetadata().entity();

        // Check if inventory item is null (structural validation)
        if (inventoryItem == null) {
            return EvaluationOutcome.fail("InventoryItem entity is null",
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate basic inventory item fields
        if (!inventoryItem.isValid()) {
            return EvaluationOutcome.fail("InventoryItem validation failed",
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate stock locations exist
        if (inventoryItem.getStockByLocation() == null || inventoryItem.getStockByLocation().isEmpty()) {
            return EvaluationOutcome.fail("No stock locations defined for product: " + inventoryItem.getProductId(),
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check each location for stock availability issues
        for (InventoryItem.StockByLocation stock : inventoryItem.getStockByLocation().values()) {
            
            // Validate stock location data
            if (!stock.isValid()) {
                return EvaluationOutcome.fail("Invalid stock data for location: " + stock.getLocationId(),
                                            StandardEvalReasonCategories.STRUCTURAL_FAILURE);
            }

            // Check for negative available stock (overselling prevention)
            if (stock.hasNegativeStock()) {
                return EvaluationOutcome.fail(String.format("Negative stock detected at location %s for product %s. Available: %d",
                                                         stock.getLocationId(), inventoryItem.getProductId(), stock.getAvailable()),
                                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // Check for unreasonable stock levels
            if (stock.getTotalStock() < 0) {
                return EvaluationOutcome.fail(String.format("Total stock is negative at location %s for product %s",
                                                         stock.getLocationId(), inventoryItem.getProductId()),
                                            StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            // Validate reserved stock doesn't exceed total stock
            Integer totalStock = stock.getTotalStock();
            Integer reservedStock = stock.getReserved() != null ? stock.getReserved() : 0;

            if (reservedStock > totalStock) {
                return EvaluationOutcome.fail(String.format("Reserved stock (%d) exceeds total stock (%d) at location %s for product %s",
                                                         reservedStock, totalStock, stock.getLocationId(), inventoryItem.getProductId()),
                                            StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            // Check for excessive damaged stock (warning threshold)
            Integer damagedStock = stock.getDamaged() != null ? stock.getDamaged() : 0;
            if (damagedStock > 0 && totalStock > 0) {
                double damagedPercentage = (double) damagedStock / totalStock * 100;
                if (damagedPercentage > 20) { // More than 20% damaged
                    logger.warn("High damaged stock percentage ({:.1f}%) at location {} for product {}", 
                               damagedPercentage, stock.getLocationId(), inventoryItem.getProductId());
                }
            }
        }

        // Check overall inventory health
        Integer totalAvailable = inventoryItem.getTotalAvailableStock();
        Integer totalReserved = inventoryItem.getTotalReservedStock();

        // Validate total available stock is reasonable
        if (totalAvailable < 0) {
            return EvaluationOutcome.fail("Total available stock is negative for product: " + inventoryItem.getProductId(),
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if inventory is completely depleted
        if (totalAvailable == 0 && totalReserved == 0) {
            logger.warn("Product {} has no available or reserved stock", inventoryItem.getProductId());
        }

        logger.debug("Stock availability validation passed for productId: {}, available: {}, reserved: {}", 
                    inventoryItem.getProductId(), totalAvailable, totalReserved);

        return EvaluationOutcome.success();
    }
}
