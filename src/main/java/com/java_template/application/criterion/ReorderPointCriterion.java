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
public class ReorderPointCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(ReorderPointCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public ReorderPointCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking {} for request: {}", className, request.getId());

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
        
        if (inventoryItem == null) {
            return EvaluationOutcome.fail("InventoryItem is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }
        
        if (inventoryItem.getReorderPoint() == null) {
            return EvaluationOutcome.fail("No reorder point configured", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        boolean reorderNeeded = inventoryItem.isReorderNeeded();
        if (reorderNeeded) {
            logger.info("Reorder point reached for product: {}", inventoryItem.getProductId());
            return EvaluationOutcome.success();
        } else {
            return EvaluationOutcome.fail("Reorder point not reached", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
