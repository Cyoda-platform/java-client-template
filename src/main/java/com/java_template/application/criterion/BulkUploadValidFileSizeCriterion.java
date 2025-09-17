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
 * BulkUploadValidFileSizeCriterion - Validates that the uploaded file size is within acceptable limits
 */
@Component
public class BulkUploadValidFileSizeCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100 MB

    public BulkUploadValidFileSizeCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking BulkUpload valid file size criteria for request: {}", request.getId());
        
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

        if (entity.getFileSize() == null) {
            return EvaluationOutcome.fail("File size is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getFileSize() <= 0) {
            return EvaluationOutcome.fail("File size must be positive", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getFileSize() > MAX_FILE_SIZE) {
            return EvaluationOutcome.fail("File size exceeds maximum limit of " + MAX_FILE_SIZE + " bytes", 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
