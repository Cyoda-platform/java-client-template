package com.java_template.application.criterion;

import com.java_template.application.entity.inventoryitem.version_1.InventoryItem;
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
public class CheckItemValidityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CheckItemValidityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(InventoryItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<InventoryItem> context) {
        InventoryItem entity = context.entity();
        if (entity == null) {
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getSku() == null || entity.getSku().trim().isEmpty()) {
            return EvaluationOutcome.fail("SKU is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getName() == null || entity.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getQuantity() == null) {
            return EvaluationOutcome.fail("Quantity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getQuantity() < 0) {
            return EvaluationOutcome.fail("Quantity cannot be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (entity.getSourceId() == null || entity.getSourceId().trim().isEmpty()) {
            return EvaluationOutcome.fail("SourceId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Business rule: if unitPrice is negative it's a data quality failure
        if (entity.getUnitPrice() != null && entity.getUnitPrice().signum() < 0) {
            return EvaluationOutcome.fail("UnitPrice cannot be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
