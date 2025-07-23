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
public class IsInvalidPetRegistrationJobCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public IsInvalidPetRegistrationJobCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("IsInvalidPetRegistrationJobCriterion initialized with SerializerFactory");
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
        return "IsInvalidPetRegistrationJobCriterion".equals(modelSpec.operationName()) &&
               "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Pet pet) {
        // Validation logic for an invalid Pet Registration Job
        if (pet.getPetId() == null || pet.getPetId().isBlank()) {
            return EvaluationOutcome.success(); // This criterion treats missing petId as invalid, so success here means invalid detected
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (pet.getType() == null || pet.getType().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (pet.getOwner() == null || pet.getOwner().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (pet.getRegisteredAt() == null || pet.getRegisteredAt().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            return EvaluationOutcome.success();
        }
        // Additional business rule: status must NOT be ACTIVE for invalid
        if (!"ACTIVE".equalsIgnoreCase(pet.getStatus())) {
            return EvaluationOutcome.success();
        }
        // If none of above invalid conditions met, this criterion fails (no invalid detected)
        return EvaluationOutcome.fail("Pet registration job is valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
