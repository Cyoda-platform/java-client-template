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
 * BulkUploadValidCriterion - Validates bulk upload can be processed
 * 
 * This criterion validates BulkUpload entities to ensure they can be processed:
 * 1. fileName must not be null or empty
 * 2. uploadedAt must not be null
 * 3. File must exist (simulated check)
 * 4. File must be valid JSON (simulated check)
 */
@Component
public class BulkUploadValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public BulkUploadValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking BulkUpload criteria for request: {}", request.getId());
        
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
     * Main validation logic for the BulkUpload entity
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<BulkUpload> context) {
        BulkUpload entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("BulkUpload is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate fileName
        if (entity.getFileName() == null || entity.getFileName().trim().isEmpty()) {
            logger.warn("BulkUpload fileName is null or empty for upload: {}", entity.getUploadId());
            return EvaluationOutcome.fail(
                "BulkUpload fileName must not be null or empty",
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Validate uploadedAt
        if (entity.getUploadedAt() == null) {
            logger.warn("BulkUpload uploadedAt is null for upload: {}", entity.getUploadId());
            return EvaluationOutcome.fail(
                "BulkUpload uploadedAt must not be null",
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Validate file exists (simulated check)
        if (!fileExists(entity.getFileName())) {
            logger.warn("BulkUpload file '{}' does not exist for upload: {}", entity.getFileName(), entity.getUploadId());
            return EvaluationOutcome.fail(
                String.format("File '%s' does not exist", entity.getFileName()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Validate file is valid JSON (simulated check)
        if (!isValidJsonFile(entity.getFileName())) {
            logger.warn("BulkUpload file '{}' is not valid JSON for upload: {}", entity.getFileName(), entity.getUploadId());
            return EvaluationOutcome.fail(
                String.format("File '%s' is not valid JSON", entity.getFileName()),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        // Use entity's built-in validation
        if (!entity.isValid()) {
            logger.warn("BulkUpload {} failed built-in validation", entity.getUploadId());
            return EvaluationOutcome.fail("Entity failed built-in validation", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        logger.debug("BulkUpload {} passed all validation checks", entity.getUploadId());
        return EvaluationOutcome.success();
    }

    /**
     * Simulated file existence check
     * In a real implementation, this would check actual file storage
     */
    private boolean fileExists(String fileName) {
        // Simulate file existence check
        // In real implementation, this would check file storage system
        return fileName != null && !fileName.trim().isEmpty();
    }

    /**
     * Simulated JSON file validation
     * In a real implementation, this would parse and validate the JSON file
     */
    private boolean isValidJsonFile(String fileName) {
        // Simulate JSON validation
        // In real implementation, this would:
        // 1. Read the file from storage
        // 2. Parse as JSON
        // 3. Validate structure (array of HN items)
        return fileName != null && fileName.toLowerCase().endsWith(".json");
    }
}
