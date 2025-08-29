package com.java_template.application.criterion;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CheckRequestApprovalCriterion implements CyodaCriterion {

    @Autowired
    private CriterionSerializer serializer;

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        try {
            // For this criterion, we'll assume it's evaluating Pet entities by default
            // In a real implementation, you might determine entity type from the request context
            return serializer.withRequest(request)
                .evaluateEntity(Pet.class, this::validatePetApproval)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();

        } catch (Exception e) {
            return serializer.withRequest(request)
                .evaluate(jsonNode -> EvaluationOutcome.Fail.structuralFailure("Error in approval criterion: " + e.getMessage()))
                .complete();
        }
    }

    private EvaluationOutcome validatePetApproval(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        // Check if pet entity is valid for approval
        Pet pet = context.entity();
        return validatePetExists(pet)
            .and(validatePetInApprovalProcess(pet))
            .and(validatePetAvailableForAdoption(pet));
    }

    private EvaluationOutcome validatePetExists(Pet pet) {
        return pet == null ?
            EvaluationOutcome.Fail.structuralFailure("Pet entity is null") :
            EvaluationOutcome.success();
    }

    private EvaluationOutcome validatePetInApprovalProcess(Pet pet) {
        return "APPROVAL_PROCESS".equals(pet.getStatus()) ?
            EvaluationOutcome.success() :
            EvaluationOutcome.Fail.businessRuleFailure("Pet must be in APPROVAL_PROCESS status for approval, current status: " + pet.getStatus());
    }

    private EvaluationOutcome validatePetAvailableForAdoption(Pet pet) {
        // Additional business logic for pet approval
        if (pet.getAge() != null && pet.getAge() < 0) {
            return EvaluationOutcome.Fail.dataQualityFailure("Pet age cannot be negative");
        }
        if (pet.getName() == null || pet.getName().trim().isEmpty()) {
            return EvaluationOutcome.Fail.dataQualityFailure("Pet must have a valid name");
        }
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateAdoptionRequestExists(AdoptionRequest adoptionRequest) {
        return adoptionRequest == null ?
            EvaluationOutcome.Fail.structuralFailure("Adoption request entity is null") :
            EvaluationOutcome.success();
    }

    private EvaluationOutcome validateAdoptionRequestUnderReview(AdoptionRequest adoptionRequest) {
        return "UNDER_REVIEW".equals(adoptionRequest.getStatus()) ?
            EvaluationOutcome.success() :
            EvaluationOutcome.Fail.businessRuleFailure("Adoption request must be UNDER_REVIEW for approval, current status: " + adoptionRequest.getStatus());
    }

    private EvaluationOutcome validateAdoptionRequestHasValidReferences(AdoptionRequest adoptionRequest) {
        // Validate that the adoption request has valid pet and user references
        return validatePetIdExists(adoptionRequest)
            .and(validateUserIdExists(adoptionRequest));
    }

    private EvaluationOutcome validatePetIdExists(AdoptionRequest adoptionRequest) {
        return (adoptionRequest.getPetId() == null || adoptionRequest.getPetId().trim().isEmpty()) ?
            EvaluationOutcome.Fail.dataQualityFailure("Adoption request must have a valid pet ID") :
            EvaluationOutcome.success();
    }

    private EvaluationOutcome validateUserIdExists(AdoptionRequest adoptionRequest) {
        return (adoptionRequest.getUserId() == null || adoptionRequest.getUserId().trim().isEmpty()) ?
            EvaluationOutcome.Fail.dataQualityFailure("Adoption request must have a valid user ID") :
            EvaluationOutcome.success();
    }

    @Override
    public boolean supports(OperationSpecification modelKey) {
        return "CheckRequestApprovalCriterion".equals(modelKey.operationName());
    }
}
