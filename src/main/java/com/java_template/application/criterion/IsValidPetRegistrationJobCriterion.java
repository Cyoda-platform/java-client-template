package com.java_template.application.criterion;

import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IsValidPetRegistrationJobCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public IsValidPetRegistrationJobCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("IsValidPetRegistrationJobCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(Pet.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "IsValidPetRegistrationJobCriterion".equals(modelSpec.operationName()) &&
               "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Pet pet) {
        // Validation logic for a valid Pet Registration Job
        if (pet.getPetId() == null || pet.getPetId().isBlank()) {
            return EvaluationOutcome.fail("Pet ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            return EvaluationOutcome.fail("Pet name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pet.getType() == null || pet.getType().isBlank()) {
            return EvaluationOutcome.fail("Pet type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pet.getOwner() == null || pet.getOwner().isBlank()) {
            return EvaluationOutcome.fail("Owner name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pet.getRegisteredAt() == null || pet.getRegisteredAt().isBlank()) {
            return EvaluationOutcome.fail("Registration date is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Additional business rule: status must be ACTIVE for a valid registration
        if (!"ACTIVE".equalsIgnoreCase(pet.getStatus())) {
            return EvaluationOutcome.fail("Pet status must be ACTIVE", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
