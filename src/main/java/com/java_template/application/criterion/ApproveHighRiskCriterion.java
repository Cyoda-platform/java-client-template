package com.java_template.application.criterion;

import com.example.application.entity.customer.version_1.Customer;
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
 * ApproveHighRiskCriterion - Evaluates if a customer should be approved for high-risk review
 * 
 * Checks if customer risk score >= 70 for high-risk approval in KYC Onboarding workflow.
 * This criterion determines whether a customer requires manual review due to high risk assessment.
 */
@Component
public class ApproveHighRiskCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ApproveHighRiskCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ApproveHighRiskCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Customer.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates if customer risk score meets high-risk threshold (>= 70)
     * 
     * Available StandardEvalReasonCategories:
     * - STRUCTURAL_FAILURE: For null entities, missing required fields
     * - BUSINESS_RULE_FAILURE: For business logic violations
     * - DATA_QUALITY_FAILURE: For data consistency issues
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Customer> context) {
        Customer customer = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (customer == null) {
            logger.warn("Customer is null");
            return EvaluationOutcome.fail("Customer entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!customer.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Customer is not valid");
            return EvaluationOutcome.fail("Customer entity is not valid", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Extract risk score from metadata
        if (customer.getMetadata() == null || !customer.getMetadata().containsKey("riskScore")) {
            logger.warn("Risk score not found in customer metadata");
            return EvaluationOutcome.fail("Risk score is missing from customer metadata", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        Object riskScoreObj = customer.getMetadata().get("riskScore");
        if (!(riskScoreObj instanceof Number)) {
            logger.warn("Risk score is not a number: {}", riskScoreObj);
            return EvaluationOutcome.fail("Risk score must be a numeric value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        double riskScore = ((Number) riskScoreObj).doubleValue();
        logger.debug("Customer {} has risk score: {}", customer.getId(), riskScore);

        // Check if risk score >= 70 (high-risk threshold)
        if (riskScore >= 70) {
            logger.info("Customer {} approved for high-risk review with score: {}", customer.getId(), riskScore);
            return EvaluationOutcome.success();
        }

        logger.debug("Customer {} does not meet high-risk threshold. Score: {}", customer.getId(), riskScore);
        return EvaluationOutcome.fail(
            String.format("Risk score %.2f is below high-risk threshold of 70", riskScore),
            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
        );
    }
}

