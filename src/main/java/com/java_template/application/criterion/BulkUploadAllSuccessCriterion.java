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
 * BulkUploadAllSuccessCriterion - Determines if all items in a bulk upload were processed successfully
 */
@Component
public class BulkUploadAllSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public BulkUploadAllSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking BulkUpload all success criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(BulkUpload.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<BulkUpload> context) {
        BulkUpload entity = context.entityWithMetadata().entity();

        if (entity == null) {
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if processing is complete
        if (entity.getProcessingEndTime() == null) {
            return EvaluationOutcome.fail("Processing not complete", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if all items were processed successfully
        if (entity.getTotalItems() == null || entity.getTotalItems() == 0) {
            return EvaluationOutcome.fail("No items to process", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (entity.getProcessedItems() == null || !entity.getProcessedItems().equals(entity.getTotalItems())) {
            return EvaluationOutcome.fail("Not all items were processed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (entity.getFailedItems() != null && entity.getFailedItems() > 0) {
            return EvaluationOutcome.fail("Some items failed processing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
