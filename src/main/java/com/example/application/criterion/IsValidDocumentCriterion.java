package com.example.application.criterion;

import com.example.application.entity.document_evidence.version_1.DocumentEvidence;
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
 * IsValidDocumentCriterion - Evaluates if a document is valid
 * 
 * Checks if document is valid based on metadata and integrity.
 * A document is valid if it has required fields and integrity checksum.
 */
@Component
public class IsValidDocumentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsValidDocumentCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking IsValidDocumentCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(DocumentEvidence.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates if document is valid
     * 
     * A document is valid if:
     * - All required fields are present (id, caseId, filename)
     * - Has storage path
     * - Has integrity checksum
     * - File size is positive
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<DocumentEvidence> context) {
        DocumentEvidence document = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (document == null) {
            logger.warn("DocumentEvidence is null");
            return EvaluationOutcome.fail("Document entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!document.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("DocumentEvidence is not valid");
            return EvaluationOutcome.fail("Document entity is not valid", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check storage path
        if (document.getStoragePath() == null || document.getStoragePath().isBlank()) {
            logger.warn("Document {} has no storage path", document.getId());
            return EvaluationOutcome.fail("Document storage path is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check integrity checksum
        if (document.getChecksum() == null || document.getChecksum().isBlank()) {
            logger.warn("Document {} has no integrity checksum", document.getId());
            return EvaluationOutcome.fail("Document integrity checksum is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check file size
        if (document.getSizeBytes() == null || document.getSizeBytes() <= 0) {
            logger.warn("Document {} has invalid file size", document.getId());
            return EvaluationOutcome.fail("Document file size is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.info("Document {} is valid with checksum: {}", document.getId(), document.getChecksum());
        return EvaluationOutcome.success();
    }
}

