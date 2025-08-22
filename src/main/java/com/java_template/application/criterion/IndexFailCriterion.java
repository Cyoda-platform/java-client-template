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
public class IndexFailCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IndexFailCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(StoredItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<StoredItem> context) {
         StoredItem entity = context.entity();
         if (entity == null) {
             logger.warn("IndexFailCriterion: entity is null");
             return EvaluationOutcome.fail("StoredItem entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If the stored item lacks a storageTechnicalId we consider this a data quality failure
         if (entity.getStorageTechnicalId() == null || entity.getStorageTechnicalId().isBlank()) {
             logger.warn("IndexFailCriterion: missing storageTechnicalId for StoredItem");
             return EvaluationOutcome.fail("storageTechnicalId is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // hnItem contains the serialized HN payload - it must be present for indexing to succeed
         if (entity.getHnItem() == null || entity.getHnItem().isBlank()) {
             logger.warn("IndexFailCriterion: hnItem payload missing for StoredItem {}", entity.getStorageTechnicalId());
             return EvaluationOutcome.fail("hnItem payload is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // sizeBytes should be non-null and non-negative
         if (entity.getSizeBytes() == null || entity.getSizeBytes() < 0) {
             logger.warn("IndexFailCriterion: invalid sizeBytes for StoredItem {}: {}", entity.getStorageTechnicalId(), entity.getSizeBytes());
             return EvaluationOutcome.fail("sizeBytes is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // storedAt should be present as a persistence timestamp
         if (entity.getStoredAt() == null || entity.getStoredAt().isBlank()) {
             logger.warn("IndexFailCriterion: storedAt timestamp missing for StoredItem {}", entity.getStorageTechnicalId());
             return EvaluationOutcome.fail("storedAt timestamp is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // All checks passed — do not mark as an index failure
         return EvaluationOutcome.success();
    }
}