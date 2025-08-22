package com.java_template.application.criterion;

import com.java_template.application.entity.storeditem.version_1.StoredItem;
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
public class IndexCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IndexCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(StoredItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<StoredItem> context) {
         StoredItem entity = context.entity();

         if (entity == null) {
             logger.debug("StoredItem entity is null in IndexCompleteCriterion");
             return EvaluationOutcome.fail("StoredItem entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // storageTechnicalId must be present to be considered indexed/available
         if (entity.getStorageTechnicalId() == null || entity.getStorageTechnicalId().isBlank()) {
             return EvaluationOutcome.fail("storageTechnicalId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // storedAt timestamp must be present
         if (entity.getStoredAt() == null || entity.getStoredAt().isBlank()) {
             return EvaluationOutcome.fail("storedAt timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // hnItem must contain the serialized HN item payload
         if (entity.getHnItem() == null || entity.getHnItem().isBlank()) {
             return EvaluationOutcome.fail("hnItem payload is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // sizeBytes must be present and non-negative
         if (entity.getSizeBytes() == null) {
             return EvaluationOutcome.fail("sizeBytes is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getSizeBytes() < 0) {
             return EvaluationOutcome.fail("sizeBytes must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed -> indexing considered complete
         return EvaluationOutcome.success();
    }
}