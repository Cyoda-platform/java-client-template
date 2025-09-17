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
 * HasUploadDataCriterion - Checks if upload has valid data to process
 */
@Component
public class HasUploadDataCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HasUploadDataCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking HasUploadDataCriterion for request: {}", request.getId());
        
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
     * Main validation logic for the HNItemUpload entity
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItemUpload> context) {
        HNItemUpload entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("HNItemUpload is null");
            return EvaluationOutcome.fail("HNItemUpload entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if uploadType is present and not empty
        if (entity.getUploadType() == null || entity.getUploadType().trim().isEmpty()) {
            logger.warn("HNItemUpload uploadType is null or empty");
            return EvaluationOutcome.fail("Upload type is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if uploadId is present and not empty
        if (entity.getUploadId() == null || entity.getUploadId().trim().isEmpty()) {
            logger.warn("HNItemUpload uploadId is null or empty");
            return EvaluationOutcome.fail("Upload ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate uploadType is one of the allowed values
        String uploadType = entity.getUploadType().toLowerCase();
        if (!uploadType.equals("single") && !uploadType.equals("array") && !uploadType.equals("file")) {
            logger.warn("HNItemUpload has invalid uploadType: {}", entity.getUploadType());
            return EvaluationOutcome.fail("Invalid upload type: " + entity.getUploadType() + 
                ". Must be one of: single, array, file", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // For file uploads, check if fileName is provided
        if ("file".equals(uploadType) && (entity.getFileName() == null || entity.getFileName().trim().isEmpty())) {
            logger.warn("HNItemUpload of type 'file' missing fileName");
            return EvaluationOutcome.fail("File name is required for file uploads", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Use the entity's built-in validation
        if (!entity.isValid()) {
            logger.warn("HNItemUpload failed validation: uploadId={}, uploadType={}", 
                       entity.getUploadId(), entity.getUploadType());
            return EvaluationOutcome.fail("HNItemUpload failed validation checks", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("HNItemUpload {} passed validation", entity.getUploadId());
        return EvaluationOutcome.success();
    }
}
