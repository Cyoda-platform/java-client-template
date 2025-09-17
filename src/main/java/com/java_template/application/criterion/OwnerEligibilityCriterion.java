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
 * OwnerEligibilityCriterion - Check if owner meets verification requirements
 */
@Component
public class OwnerEligibilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OwnerEligibilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Owner eligibility criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Owner.class, this::validateOwnerEligibility)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Check if owner meets verification requirements
     * Business rule: owner.email != null AND owner.firstName != null AND owner.lastName != null AND owner.address != null
     */
    private EvaluationOutcome validateOwnerEligibility(CriterionSerializer.CriterionEntityEvaluationContext<Owner> context) {
        Owner owner = context.entityWithMetadata().entity();

        // Check if owner is null (structural validation)
        if (owner == null) {
            logger.warn("Owner entity is null");
            return EvaluationOutcome.fail("Owner entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!owner.isValid()) {
            logger.warn("Owner entity is not valid");
            return EvaluationOutcome.fail("Owner entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check email
        if (owner.getEmail() == null || owner.getEmail().trim().isEmpty()) {
            logger.warn("Owner {} has no email address", owner.getOwnerId());
            return EvaluationOutcome.fail(
                String.format("Owner %s has no email address", owner.getOwnerId()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Check first name
        if (owner.getFirstName() == null || owner.getFirstName().trim().isEmpty()) {
            logger.warn("Owner {} has no first name", owner.getOwnerId());
            return EvaluationOutcome.fail(
                String.format("Owner %s has no first name", owner.getOwnerId()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Check last name
        if (owner.getLastName() == null || owner.getLastName().trim().isEmpty()) {
            logger.warn("Owner {} has no last name", owner.getOwnerId());
            return EvaluationOutcome.fail(
                String.format("Owner %s has no last name", owner.getOwnerId()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Check address
        if (owner.getAddress() == null) {
            logger.warn("Owner {} has no address", owner.getOwnerId());
            return EvaluationOutcome.fail(
                String.format("Owner %s has no address", owner.getOwnerId()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Additional address validation
        if (owner.getAddress().getLine1() == null || owner.getAddress().getLine1().trim().isEmpty()) {
            logger.warn("Owner {} has incomplete address (missing line1)", owner.getOwnerId());
            return EvaluationOutcome.fail(
                String.format("Owner %s has incomplete address (missing line1)", owner.getOwnerId()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        if (owner.getAddress().getCity() == null || owner.getAddress().getCity().trim().isEmpty()) {
            logger.warn("Owner {} has incomplete address (missing city)", owner.getOwnerId());
            return EvaluationOutcome.fail(
                String.format("Owner %s has incomplete address (missing city)", owner.getOwnerId()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        logger.debug("Owner {} meets all eligibility requirements", owner.getOwnerId());
        return EvaluationOutcome.success();
    }
}
