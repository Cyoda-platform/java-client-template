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
 * AdoptionApprovalCriterion - Check if adoption can be approved
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
        logger.debug("Checking Adoption approval criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Adoption.class, this::validateAdoptionApproval)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Check if adoption can be approved
     * Business rule: adoption.homeVisitPassed == true AND adoption.contractSigned == true
     */
    private EvaluationOutcome validateAdoptionApproval(CriterionSerializer.CriterionEntityEvaluationContext<Adoption> context) {
        Adoption adoption = context.entityWithMetadata().entity();

        // Check if adoption is null (structural validation)
        if (adoption == null) {
            logger.warn("Adoption entity is null");
            return EvaluationOutcome.fail("Adoption entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!adoption.isValid()) {
            logger.warn("Adoption entity is not valid");
            return EvaluationOutcome.fail("Adoption entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check home visit status
        if (adoption.getHomeVisitPassed() == null || !adoption.getHomeVisitPassed()) {
            logger.warn("Adoption {} home visit has not passed", adoption.getAdoptionId());
            return EvaluationOutcome.fail(
                String.format("Adoption %s home visit has not passed", adoption.getAdoptionId()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Check contract signing status
        if (adoption.getContractSigned() == null || !adoption.getContractSigned()) {
            logger.warn("Adoption {} contract has not been signed", adoption.getAdoptionId());
            return EvaluationOutcome.fail(
                String.format("Adoption %s contract has not been signed", adoption.getAdoptionId()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        logger.debug("Adoption {} meets all approval requirements", adoption.getAdoptionId());
        return EvaluationOutcome.success();
    }
}
