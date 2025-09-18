package com.java_template.application.criterion;

import com.java_template.application.entity.owner.version_1.Owner;
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
 * OwnerVerificationCriterion - Checks if owner meets verification requirements
 * 
 * This criterion validates that an owner has all required information
 * and meets the criteria for verification before allowing the transition
 * from registered to verified state.
 */
@Component
public class OwnerVerificationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OwnerVerificationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking OwnerVerification criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Owner.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for owner verification
     * 
     * Validates that:
     * - Owner is in registered state
     * - Owner has all required personal information
     * - Owner has valid contact information
     * - Owner has housing information for pet matching
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Owner> context) {
        Owner owner = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if owner entity is null
        if (owner == null) {
            logger.warn("Owner entity is null");
            return EvaluationOutcome.fail("Owner entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if owner is valid
        if (!owner.isValid()) {
            logger.warn("Owner {} {} is not valid", owner.getFirstName(), owner.getLastName());
            return EvaluationOutcome.fail("Owner is not valid", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if owner is in registered state
        if (!"registered".equals(currentState)) {
            logger.warn("Owner {} {} is not in registered state (current: {})", 
                       owner.getFirstName(), owner.getLastName(), currentState);
            return EvaluationOutcome.fail("Owner must be in registered state for verification", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if email is valid (basic validation)
        if (owner.getEmail() == null || !owner.getEmail().contains("@")) {
            logger.warn("Owner {} {} has invalid email: {}", 
                       owner.getFirstName(), owner.getLastName(), owner.getEmail());
            return EvaluationOutcome.fail("Owner must have valid email address", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if housing type is provided (important for pet matching)
        if (owner.getHousingType() == null || owner.getHousingType().trim().isEmpty()) {
            logger.warn("Owner {} {} has no housing type specified", 
                       owner.getFirstName(), owner.getLastName());
            return EvaluationOutcome.fail("Owner must specify housing type for verification", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if registration date is set
        if (owner.getRegistrationDate() == null) {
            logger.warn("Owner {} {} has no registration date", 
                       owner.getFirstName(), owner.getLastName());
            return EvaluationOutcome.fail("Owner must have registration date", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("Owner {} {} passed all verification criteria", 
                    owner.getFirstName(), owner.getLastName());
        return EvaluationOutcome.success();
    }
}
