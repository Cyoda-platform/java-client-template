package com.java_template.application.criterion;

import com.java_template.application.entity.customer.version_1.Customer;
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
 * Criterion to check verification result status
 * 
 * This criterion evaluates whether a customer's verification has been successful.
 * It's used in the workflow to automatically transition from verification_pending
 * to verified state when verification is successful.
 * 
 * The criterion checks the verification.status field for "SUCCESS" value.
 */
@Component
public class checkVerificationResult implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public checkVerificationResult(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking verification result for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Customer.class, this::validateVerificationStatus)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic to check verification status
     * 
     * This method checks if the customer's verification status is "SUCCESS"
     * which indicates that the external verification provider has successfully
     * verified the customer's identity.
     */
    private EvaluationOutcome validateVerificationStatus(CriterionSerializer.CriterionEntityEvaluationContext<Customer> context) {
        Customer customer = context.entityWithMetadata().entity();

        // Check if customer is null (structural validation)
        if (customer == null) {
            logger.warn("Customer entity is null");
            return EvaluationOutcome.fail("Customer entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if customer is valid
        if (!customer.isValid()) {
            logger.warn("Customer entity is not valid: {}", customer.getCustomerId());
            return EvaluationOutcome.fail("Customer entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if verification info exists
        Customer.VerificationInfo verification = customer.getVerification();
        if (verification == null) {
            logger.warn("No verification information found for customer: {}", customer.getCustomerId());
            return EvaluationOutcome.fail("No verification information available", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check verification status
        String status = verification.getStatus();
        if (status == null || status.trim().isEmpty()) {
            logger.warn("Verification status is null or empty for customer: {}", customer.getCustomerId());
            return EvaluationOutcome.fail("Verification status is not set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if verification is successful
        if ("SUCCESS".equalsIgnoreCase(status.trim())) {
            logger.info("Verification successful for customer: {}", customer.getCustomerId());
            return EvaluationOutcome.success();
        }

        // Verification is not successful
        logger.debug("Verification not successful for customer: {} - status: {}", customer.getCustomerId(), status);
        return EvaluationOutcome.fail(
            String.format("Verification status is '%s', expected 'SUCCESS'", status), 
            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
        );
    }
}
