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

import java.nio.charset.StandardCharsets;

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
             return EvaluationOutcome.fail("StoredItem entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // storageTechnicalId is required to reference the persisted item for indexing
         String storageId = entity.getStorageTechnicalId();
         if (storageId == null || storageId.isBlank()) {
             return EvaluationOutcome.fail("storageTechnicalId is required for indexing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // storedAt timestamp must be present to indicate persistence time
         String storedAt = entity.getStoredAt();
         if (storedAt == null || storedAt.isBlank()) {
             return EvaluationOutcome.fail("storedAt timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // hnItem payload must exist for building indexes
         String hnItem = entity.getHnItem();
         if (hnItem == null || hnItem.isBlank()) {
             return EvaluationOutcome.fail("hnItem payload is missing or empty", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // sizeBytes should be present and positive
         Integer sizeBytes = entity.getSizeBytes();
         if (sizeBytes == null || sizeBytes <= 0) {
             return EvaluationOutcome.fail("sizeBytes must be a positive integer", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic consistency check between declared sizeBytes and actual payload size.
         try {
             int actualBytes = hnItem.getBytes(StandardCharsets.UTF_8).length;
             if (Math.abs(actualBytes - sizeBytes) > 1024) { // tolerance of 1KB for metadata differences
                 return EvaluationOutcome.fail("sizeBytes differs significantly from actual hnItem payload size", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } catch (Exception e) {
             logger.warn("Failed to compute hnItem byte size for StoredItem {}: {}", storageId, e.getMessage());
             return EvaluationOutcome.fail("unable to validate hnItem payload size", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}