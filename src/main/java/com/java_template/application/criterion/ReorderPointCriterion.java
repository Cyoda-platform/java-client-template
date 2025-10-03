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
 * ABOUTME: ReorderPointCriterion evaluates whether inventory has reached the reorder point
 * and triggers automatic transition to ReorderRequired state when stock is low.
 */
@Component
public class ReorderPointCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReorderPointCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Reorder point criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(InventoryItem.class, this::validateReorderPoint)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateReorderPoint(CriterionSerializer.CriterionEntityEvaluationContext<InventoryItem> context) {
        InventoryItem inventoryItem = context.entityWithMetadata().entity();

        // Check if inventory item is null (structural validation)
        if (inventoryItem == null) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.STRUCTURAL_FAILURE, 
                                        "InventoryItem entity is null");
        }

        // Validate basic inventory item fields
        if (!inventoryItem.isValid()) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.STRUCTURAL_FAILURE, 
                                        "InventoryItem validation failed");
        }

        // Validate reorder point is configured
        if (inventoryItem.getReorderPoint() == null) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.CONFIGURATION_FAILURE, 
                                        "Reorder point not configured for product: " + inventoryItem.getProductId());
        }

        if (inventoryItem.getReorderPoint() < 0) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.CONFIGURATION_FAILURE, 
                                        "Invalid reorder point (negative) for product: " + inventoryItem.getProductId());
        }

        // Validate reorder quantity is configured
        if (inventoryItem.getReorderQuantity() == null || inventoryItem.getReorderQuantity() <= 0) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.CONFIGURATION_FAILURE, 
                                        "Invalid reorder quantity for product: " + inventoryItem.getProductId());
        }

        // Check if reorder is needed
        Integer totalAvailable = inventoryItem.getTotalAvailableStock();
        Integer reorderPoint = inventoryItem.getReorderPoint();

        if (totalAvailable <= reorderPoint) {
            logger.info("Reorder needed for product {}: available stock ({}) is at or below reorder point ({})", 
                       inventoryItem.getProductId(), totalAvailable, reorderPoint);
            
            // Return success to trigger the transition to ReorderRequired state
            return EvaluationOutcome.success();
        }

        // Stock is above reorder point - criterion fails (no transition needed)
        logger.debug("Stock level sufficient for product {}: available ({}) > reorder point ({})", 
                    inventoryItem.getProductId(), totalAvailable, reorderPoint);
        
        return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                    String.format("Stock level (%d) is above reorder point (%d) for product %s", 
                                                 totalAvailable, reorderPoint, inventoryItem.getProductId()));
    }
}
