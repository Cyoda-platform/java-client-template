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
public class CheckRequestRejectionCriterion implements CyodaCriterion {

    @Autowired
    private CriterionSerializer serializer;

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        try {
            // For this criterion, we'll assume it's evaluating Pet entities by default
            // In a real implementation, you might determine entity type from the request context
            return serializer.withRequest(request)
                .evaluateEntity(Pet.class, this::validatePetRejection)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();

        } catch (Exception e) {
            return serializer.withRequest(request)
                .evaluate(jsonNode -> EvaluationOutcome.Fail.structuralFailure("Error in rejection criterion: " + e.getMessage()))
                .complete();
        }
    }

    private EvaluationOutcome validatePetRejection(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        // Check if pet entity should be rejected (return to available status)
        Pet pet = context.entity();
        return validatePetExists(pet)
            .and(validatePetInApprovalProcess(pet))
            .and(checkPetRejectionConditions(pet));
    }

    private EvaluationOutcome validatePetExists(Pet pet) {
        return pet == null ?
            EvaluationOutcome.Fail.structuralFailure("Pet entity is null") :
            EvaluationOutcome.success();
    }

    private EvaluationOutcome validatePetInApprovalProcess(Pet pet) {
        return "APPROVAL_PROCESS".equals(pet.getStatus()) ?
            EvaluationOutcome.success() :
            EvaluationOutcome.Fail.businessRuleFailure("Pet must be in APPROVAL_PROCESS status for rejection evaluation, current status: " + pet.getStatus());
    }

    private EvaluationOutcome checkPetRejectionConditions(Pet pet) {
        // Business logic for when a pet adoption should be rejected
        // For demonstration, we'll check some basic conditions that would lead to rejection
        
        // Check if pet has invalid data that would cause rejection
        if (pet.getAge() != null && pet.getAge() > 20) {
            return EvaluationOutcome.success(); // Reject if pet is too old (example business rule)
        }
        
        if (pet.getType() != null && "Exotic".equalsIgnoreCase(pet.getType())) {
            return EvaluationOutcome.success(); // Reject exotic pets (example business rule)
        }
        
        // If no rejection conditions are met, this criterion fails (meaning don't reject)
        return EvaluationOutcome.Fail.businessRuleFailure("No rejection conditions met for pet");
    }

    private EvaluationOutcome validateAdoptionRequestExists(AdoptionRequest adoptionRequest) {
        return adoptionRequest == null ?
            EvaluationOutcome.Fail.structuralFailure("Adoption request entity is null") :
            EvaluationOutcome.success();
    }

    private EvaluationOutcome validateAdoptionRequestUnderReview(AdoptionRequest adoptionRequest) {
        return "UNDER_REVIEW".equals(adoptionRequest.getStatus()) ?
            EvaluationOutcome.success() :
            EvaluationOutcome.Fail.businessRuleFailure("Adoption request must be UNDER_REVIEW for rejection evaluation, current status: " + adoptionRequest.getStatus());
    }

    private EvaluationOutcome checkAdoptionRequestRejectionConditions(AdoptionRequest adoptionRequest) {
        // Business logic for when an adoption request should be rejected
        // For demonstration, we'll implement some basic rejection criteria
        
        // Check if adoption request has invalid references
        if (adoptionRequest.getPetId() == null || adoptionRequest.getPetId().trim().isEmpty()) {
            return EvaluationOutcome.success(); // Reject if no valid pet ID
        }
        
        if (adoptionRequest.getUserId() == null || adoptionRequest.getUserId().trim().isEmpty()) {
            return EvaluationOutcome.success(); // Reject if no valid user ID
        }
        
        // Additional business rules could be added here
        // For example: check user's adoption history, pet compatibility, etc.
        
        // If no rejection conditions are met, this criterion fails (meaning don't reject)
        return EvaluationOutcome.Fail.businessRuleFailure("No rejection conditions met for adoption request");
    }

    @Override
    public boolean supports(OperationSpecification modelKey) {
        return "CheckRequestRejectionCriterion".equals(modelKey.operationName());
    }
}
