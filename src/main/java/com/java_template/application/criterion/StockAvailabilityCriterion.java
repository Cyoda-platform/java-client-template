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

@Component
public class StockAvailabilityCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(StockAvailabilityCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public StockAvailabilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(InventoryItem.class, this::validateAvailability)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateAvailability(CriterionSerializer.CriterionEntityEvaluationContext<InventoryItem> context) {
        InventoryItem inventoryItem = context.entityWithMetadata().entity();
        
        if (inventoryItem == null) {
            return EvaluationOutcome.fail("InventoryItem is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }
        
        int totalAvailable = inventoryItem.getTotalAvailableStock();
        if (totalAvailable <= 0) {
            return EvaluationOutcome.fail("No available stock", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        logger.debug("Stock availability check passed for inventory item: {} - Available: {}", 
                    inventoryItem.getProductId(), totalAvailable);
        return EvaluationOutcome.success();
    }
}
