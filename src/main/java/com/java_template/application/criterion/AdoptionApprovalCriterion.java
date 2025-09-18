package com.java_template.application.criterion;

import com.java_template.application.entity.adoption.version_1.Adoption;
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
 * AdoptionApprovalCriterion - Checks if adoption can be approved
 * 
 * This criterion validates that an adoption application meets all
 * requirements for approval before allowing the transition from
 * pending to approved state.
 */
@Component
public class AdoptionApprovalCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AdoptionApprovalCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking AdoptionApproval criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Adoption.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for adoption approval
     * 
     * Validates that:
     * - Adoption is in pending state
     * - Adoption has valid pet and owner IDs
     * - Adoption fee is confirmed
     * - Application date is set
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Adoption> context) {
        Adoption adoption = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if adoption entity is null
        if (adoption == null) {
            logger.warn("Adoption entity is null");
            return EvaluationOutcome.fail("Adoption entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if adoption is valid
        if (!adoption.isValid()) {
            logger.warn("Adoption for pet {} and owner {} is not valid", 
                       adoption.getPetId(), adoption.getOwnerId());
            return EvaluationOutcome.fail("Adoption is not valid", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if adoption is in pending state
        if (!"pending".equals(currentState)) {
            logger.warn("Adoption for pet {} and owner {} is not in pending state (current: {})", 
                       adoption.getPetId(), adoption.getOwnerId(), currentState);
            return EvaluationOutcome.fail("Adoption must be in pending state for approval", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if pet ID is valid
        if (adoption.getPetId() == null || adoption.getPetId().trim().isEmpty()) {
            logger.warn("Adoption has invalid pet ID: {}", adoption.getPetId());
            return EvaluationOutcome.fail("Adoption must have valid pet ID", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if owner ID is valid
        if (adoption.getOwnerId() == null || adoption.getOwnerId().trim().isEmpty()) {
            logger.warn("Adoption has invalid owner ID: {}", adoption.getOwnerId());
            return EvaluationOutcome.fail("Adoption must have valid owner ID", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if adoption fee is confirmed
        if (adoption.getAdoptionFee() == null || adoption.getAdoptionFee() < 0) {
            logger.warn("Adoption for pet {} and owner {} has invalid adoption fee: {}", 
                       adoption.getPetId(), adoption.getOwnerId(), adoption.getAdoptionFee());
            return EvaluationOutcome.fail("Adoption must have confirmed adoption fee", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if application date is set
        if (adoption.getApplicationDate() == null) {
            logger.warn("Adoption for pet {} and owner {} has no application date", 
                       adoption.getPetId(), adoption.getOwnerId());
            return EvaluationOutcome.fail("Adoption must have application date", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("Adoption for pet {} and owner {} passed all approval criteria", 
                    adoption.getPetId(), adoption.getOwnerId());
        return EvaluationOutcome.success();
    }
}
