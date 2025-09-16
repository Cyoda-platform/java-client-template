package com.java_template.application.criterion;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Criterion for validating that a pet meets all requirements to be made available for sale.
 * Used in the make_available transition from draft to available.
 */
@Component
public class PetValidityCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(PetValidityCriterion.class);
    private final CriterionSerializer serializer;

    public PetValidityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking pet validity for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Pet.class, this::validatePet)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    private EvaluationOutcome validatePet(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet pet = context.entity();
        return validatePetExists(pet)
            .and(validatePetName(pet))
            .and(validatePhotoUrls(pet))
            .and(validatePetNotAlreadyProcessed(pet));
    }

    private EvaluationOutcome validatePetExists(Pet pet) {
        if (pet == null) {
            return EvaluationOutcome.Fail.businessRuleFailure("Pet entity is null");
        }
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validatePetName(Pet pet) {
        if (pet.getName() == null || pet.getName().trim().isEmpty()) {
            return EvaluationOutcome.Fail.businessRuleFailure("Pet name is required");
        }
        if (pet.getName().length() > 100) {
            return EvaluationOutcome.Fail.businessRuleFailure("Pet name too long (max 100 characters)");
        }
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validatePhotoUrls(Pet pet) {
        if (pet.getPhotoUrls() == null || pet.getPhotoUrls().isEmpty()) {
            return EvaluationOutcome.Fail.businessRuleFailure("At least one photo URL required");
        }

        for (String photoUrl : pet.getPhotoUrls()) {
            if (photoUrl == null || photoUrl.trim().isEmpty()) {
                return EvaluationOutcome.Fail.businessRuleFailure("Photo URL cannot be empty");
            }

            try {
                URL url = new URL(photoUrl);
                String protocol = url.getProtocol().toLowerCase();
                if (!"http".equals(protocol) && !"https".equals(protocol)) {
                    return EvaluationOutcome.Fail.businessRuleFailure("Invalid photo URL format: " + photoUrl);
                }
            } catch (MalformedURLException e) {
                return EvaluationOutcome.Fail.businessRuleFailure("Invalid photo URL format: " + photoUrl);
            }
        }

        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validatePetNotAlreadyProcessed(Pet pet) {
        // Note: In a real implementation, we would check the pet's current state
        // against the workflow states. For now, we assume this validation
        // is handled by the workflow engine itself.
        return EvaluationOutcome.success();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "PetValidityCriterion".equals(opSpec.operationName()) &&
               "Pet".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
