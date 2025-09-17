package com.java_template.application.criterion;

import com.java_template.application.entity.hnitemupload.version_1.HNItemUpload;
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
 * ProcessingSuccessfulCriterion - Determines if upload processing was successful
 */
@Component
public class ProcessingSuccessfulCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProcessingSuccessfulCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ProcessingSuccessfulCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(HNItemUpload.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for determining processing success
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItemUpload> context) {
        HNItemUpload entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("HNItemUpload is null");
            return EvaluationOutcome.fail("HNItemUpload entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if failedItems is set and equals 0
        if (entity.getFailedItems() == null) {
            logger.warn("HNItemUpload failedItems is null for upload: {}", entity.getUploadId());
            return EvaluationOutcome.fail("Failed items count is not set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (entity.getFailedItems() > 0) {
            logger.warn("HNItemUpload has failed items: {} for upload: {}", 
                       entity.getFailedItems(), entity.getUploadId());
            return EvaluationOutcome.fail("Upload has " + entity.getFailedItems() + " failed items", 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if processedItems is set and > 0
        if (entity.getProcessedItems() == null) {
            logger.warn("HNItemUpload processedItems is null for upload: {}", entity.getUploadId());
            return EvaluationOutcome.fail("Processed items count is not set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (entity.getProcessedItems() <= 0) {
            logger.warn("HNItemUpload has no processed items: {} for upload: {}", 
                       entity.getProcessedItems(), entity.getUploadId());
            return EvaluationOutcome.fail("Upload has no successfully processed items", 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Additional validation: check if totalItems matches processedItems + failedItems
        if (entity.getTotalItems() != null) {
            int expectedTotal = entity.getProcessedItems() + entity.getFailedItems();
            if (!entity.getTotalItems().equals(expectedTotal)) {
                logger.warn("HNItemUpload total items mismatch: expected {}, got {} for upload: {}", 
                           expectedTotal, entity.getTotalItems(), entity.getUploadId());
                // This is a warning, not a failure - processing can still be considered successful
            }
        }

        logger.debug("HNItemUpload {} passed success validation with {} processed items and {} failed items", 
                    entity.getUploadId(), entity.getProcessedItems(), entity.getFailedItems());
        return EvaluationOutcome.success();
    }
}
