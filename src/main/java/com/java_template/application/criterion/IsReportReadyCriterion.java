package com.java_template.application.criterion;

import com.example.application.entity.case_entity.version_1.Case;
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
 * IsReportReadyCriterion - Evaluates if a report/case is ready for submission
 * 
 * Checks if report is ready for submission based on case status and evidence.
 * A report is ready if status is RESOLVED and has evidence attached.
 */
@Component
public class IsReportReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsReportReadyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking IsReportReadyCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Case.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates if report/case is ready for submission
     * 
     * A report is ready if:
     * - Case status is RESOLVED, AND
     * - Has evidence attached (evidences list is not empty)
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Case> context) {
        Case caseEntity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (caseEntity == null) {
            logger.warn("Case is null");
            return EvaluationOutcome.fail("Case entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!caseEntity.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Case is not valid");
            return EvaluationOutcome.fail("Case entity is not valid", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        Case.CaseStatus status = caseEntity.getStatus();
        logger.debug("Case {} has status: {}", caseEntity.getId(), status);

        // Check if case status is RESOLVED
        if (status != Case.CaseStatus.RESOLVED) {
            logger.debug("Case {} is not resolved. Current status: {}", caseEntity.getId(), status);
            return EvaluationOutcome.fail(
                String.format("Case status must be RESOLVED for submission (current: %s)", status),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Check if evidence is attached
        if (caseEntity.getEvidences() == null || caseEntity.getEvidences().isEmpty()) {
            logger.warn("Case {} has no evidence attached", caseEntity.getId());
            return EvaluationOutcome.fail(
                "Case must have evidence attached before submission",
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        logger.info("Case {} is ready for submission with {} evidence items", caseEntity.getId(), caseEntity.getEvidences().size());
        return EvaluationOutcome.success();
    }
}

