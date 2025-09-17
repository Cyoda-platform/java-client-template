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
 * OwnerEligibilityCriterion - Checks if owner meets adoption requirements
 * 
 * Purpose: Verify owner has all required information for pet adoption approval
 * Used in: verified -> approved transition
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
        logger.debug("Checking OwnerEligibility criteria for request: {}", request.getId());
        
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
     * Main validation logic for the owner entity
     * Checks if owner has email, phone, and address information
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Owner> context) {
        Owner entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("Owner entity is null");
            return EvaluationOutcome.fail("Owner entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid()) {
            logger.warn("Owner entity is not valid");
            return EvaluationOutcome.fail("Owner entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if owner.email is not null
        if (entity.getEmail() == null || entity.getEmail().trim().isEmpty()) {
            logger.warn("Owner {} has no email address", entity.getOwnerId());
            return EvaluationOutcome.fail(
                String.format("Owner %s has no email address", entity.getOwnerId()), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if owner.phone is not null
        if (entity.getPhone() == null || entity.getPhone().trim().isEmpty()) {
            logger.warn("Owner {} has no phone number", entity.getOwnerId());
            return EvaluationOutcome.fail(
                String.format("Owner %s has no phone number", entity.getOwnerId()), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if owner.address is not null
        if (entity.getAddress() == null) {
            logger.warn("Owner {} has no address information", entity.getOwnerId());
            return EvaluationOutcome.fail(
                String.format("Owner %s has no address information", entity.getOwnerId()), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Additional validation: check if address is complete
        if (!entity.getAddress().isValid()) {
            logger.warn("Owner {} has incomplete address information", entity.getOwnerId());
            return EvaluationOutcome.fail(
                String.format("Owner %s has incomplete address information", entity.getOwnerId()), 
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.info("Owner {} meets all eligibility requirements", entity.getOwnerId());
        
        return EvaluationOutcome.success();
    }
}
