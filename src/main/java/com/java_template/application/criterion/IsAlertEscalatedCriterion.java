package com.java_template.application.criterion;

import com.example.application.entity.alert.version_1.Alert;
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
 * IsAlertEscalatedCriterion - Evaluates if an alert should be escalated
 * 
 * Checks if alert should be escalated based on severity and status.
 * An alert should be escalated if severity is HIGH or status is IN_REVIEW.
 */
@Component
public class IsAlertEscalatedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsAlertEscalatedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking IsAlertEscalatedCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Alert.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates if alert should be escalated
     * 
     * An alert should be escalated if:
     * - Severity is HIGH, OR
     * - Status is IN_REVIEW
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Alert> context) {
        Alert alert = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (alert == null) {
            logger.warn("Alert is null");
            return EvaluationOutcome.fail("Alert entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!alert.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Alert is not valid");
            return EvaluationOutcome.fail("Alert entity is not valid", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        Alert.Severity severity = alert.getSeverity();
        Alert.Status status = alert.getStatus();

        logger.debug("Alert {} has severity: {}, status: {}", alert.getId(), severity, status);

        // Alert should be escalated if severity is HIGH
        if (severity == Alert.Severity.HIGH) {
            logger.info("Alert {} should be escalated due to HIGH severity", alert.getId());
            return EvaluationOutcome.success();
        }

        // Alert should be escalated if status is IN_REVIEW
        if (status == Alert.Status.IN_REVIEW) {
            logger.info("Alert {} should be escalated due to IN_REVIEW status", alert.getId());
            return EvaluationOutcome.success();
        }

        logger.debug("Alert {} does not require escalation", alert.getId());
        return EvaluationOutcome.fail(
            String.format("Alert does not require escalation (Severity: %s, Status: %s)", severity, status),
            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
        );
    }
}

