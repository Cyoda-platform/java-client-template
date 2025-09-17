package com.java_template.application.criterion;

import com.java_template.application.entity.bulkupload.version_1.BulkUpload;
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
 * ProcessingCompleteCriterion - Determine if processing is complete and outcome
 * 
 * This criterion checks if bulk upload processing is complete by verifying:
 * - Total processed items (successful + failed) >= total items
 * - Used to determine final state transition (completed, completed_with_errors, or failed)
 */
@Component
public class ProcessingCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProcessingCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ProcessingComplete criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(BulkUpload.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic to determine if processing is complete
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<BulkUpload> context) {
        BulkUpload entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("BulkUpload is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Get counters with null safety
        Integer totalItems = entity.getTotalItems() != null ? entity.getTotalItems() : 0;
        Integer processedItems = entity.getProcessedItems() != null ? entity.getProcessedItems() : 0;
        Integer failedItems = entity.getFailedItems() != null ? entity.getFailedItems() : 0;

        // Calculate total processed (successful + failed)
        int totalProcessed = processedItems + failedItems;

        logger.debug("BulkUpload {} processing status: Total={}, Processed={}, Failed={}, TotalProcessed={}", 
                    entity.getUploadId(), totalItems, processedItems, failedItems, totalProcessed);

        // Check if processing is complete
        boolean isComplete = totalProcessed >= totalItems;

        if (!isComplete) {
            logger.debug("BulkUpload {} processing not yet complete: {}/{} items processed", 
                        entity.getUploadId(), totalProcessed, totalItems);
            return EvaluationOutcome.fail(
                String.format("Processing not complete: %d/%d items processed", totalProcessed, totalItems),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        logger.info("BulkUpload {} processing complete: {}/{} items processed", 
                   entity.getUploadId(), totalProcessed, totalItems);
        return EvaluationOutcome.success();
    }
}
