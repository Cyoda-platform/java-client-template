package com.java_template.application.criterion;

import com.java_template.application.entity.document.version_1.Document;
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
 * DocumentPermissionCriterion - Validates document upload permissions
 * Checks if user has permission to upload documents for the submission
 */
@Component
public class DocumentPermissionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DocumentPermissionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Document permission criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Document.class, this::validateDocumentPermission)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for document upload permissions
     */
    private EvaluationOutcome validateDocumentPermission(CriterionSerializer.CriterionEntityEvaluationContext<Document> context) {
        Document document = context.entityWithMetadata().entity();

        // Check if document is null (structural validation)
        if (document == null) {
            logger.warn("Document is null");
            return EvaluationOutcome.fail("Document is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check basic entity validity
        if (!document.isValid()) {
            logger.warn("Document is not valid: {}", document.getFileName());
            return EvaluationOutcome.fail("Document is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if uploader email is provided
        if (document.getUploadedBy() == null || document.getUploadedBy().trim().isEmpty()) {
            logger.warn("No uploader specified for document: {}", document.getFileName());
            return EvaluationOutcome.fail("Uploader must be specified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check uploader email format
        if (!isValidEmailFormat(document.getUploadedBy())) {
            logger.warn("Invalid uploader email format: {}", document.getUploadedBy());
            return EvaluationOutcome.fail("Invalid uploader email format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if submission ID is provided
        if (document.getSubmissionId() == null || document.getSubmissionId().trim().isEmpty()) {
            logger.warn("No submission ID specified for document: {}", document.getFileName());
            return EvaluationOutcome.fail("Submission ID must be specified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate submission ID format (should be UUID)
        if (!isValidUUIDFormat(document.getSubmissionId())) {
            logger.warn("Invalid submission ID format: {}", document.getSubmissionId());
            return EvaluationOutcome.fail("Invalid submission ID format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check file name is provided
        if (document.getFileName() == null || document.getFileName().trim().isEmpty()) {
            logger.warn("No file name specified for document upload");
            return EvaluationOutcome.fail("File name must be specified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check file type is provided and valid
        if (document.getFileType() == null || document.getFileType().trim().isEmpty()) {
            logger.warn("No file type specified for document: {}", document.getFileName());
            return EvaluationOutcome.fail("File type must be specified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check file size is reasonable
        if (document.getFileSize() == null || document.getFileSize() <= 0) {
            logger.warn("Invalid file size for document: {}", document.getFileName());
            return EvaluationOutcome.fail("Valid file size must be specified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check file size limits
        if (document.getFileSize() > 104857600L) { // 100MB
            logger.warn("File size too large for document: {} ({})", document.getFileName(), document.getFileSize());
            return EvaluationOutcome.fail("File size exceeds maximum limit of 100MB", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates email format
     */
    private boolean isValidEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return email.contains("@") && email.contains(".") && 
               email.indexOf("@") > 0 && 
               email.indexOf("@") < email.lastIndexOf(".");
    }

    /**
     * Validates UUID format (simplified)
     */
    private boolean isValidUUIDFormat(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            return false;
        }
        // Simple UUID format check (8-4-4-4-12 pattern)
        return uuid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }
}
