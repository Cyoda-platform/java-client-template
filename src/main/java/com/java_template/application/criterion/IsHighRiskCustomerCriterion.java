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
 * IsHighRiskCustomerCriterion - Evaluates if a customer is classified as high-risk
 * 
 * Checks if customer is high-risk based on KYC level and status.
 * A customer is considered high-risk if KYC level is HIGH or status is SUSPENDED.
 */
@Component
public class IsHighRiskCustomerCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsHighRiskCustomerCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking IsHighRiskCustomerCriterion for request: {}", request.getId());

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
     * Validates if customer is classified as high-risk
     * 
     * A customer is high-risk if:
     * - KYC level is HIGH, OR
     * - Customer status is SUSPENDED
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

        // Check KYC level
        Customer.KycLevel kycLevel = customer.getKycLevel();
        Customer.Status status = customer.getStatus();

        logger.debug("Customer {} has KYC level: {}, Status: {}", customer.getId(), kycLevel, status);

        // Customer is high-risk if KYC level is HIGH
        if (kycLevel == Customer.KycLevel.HIGH) {
            logger.info("Customer {} is high-risk due to HIGH KYC level", customer.getId());
            return EvaluationOutcome.success();
        }

        // Customer is high-risk if status is SUSPENDED
        if (status == Customer.Status.SUSPENDED) {
            logger.info("Customer {} is high-risk due to SUSPENDED status", customer.getId());
            return EvaluationOutcome.success();
        }

        logger.debug("Customer {} is not classified as high-risk", customer.getId());
        return EvaluationOutcome.fail(
            String.format("Customer is not high-risk (KYC: %s, Status: %s)", kycLevel, status),
            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
        );
    }
}

