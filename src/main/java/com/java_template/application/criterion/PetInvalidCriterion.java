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
public class PetInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetInvalidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetInvalidCriterion initialized with SerializerFactory");
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
        return "PetInvalidCriterion".equals(modelSpec.operationName()) &&
               "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Pet pet) {
        // Pet is invalid if any required field is missing or blank
        if (pet.getPetId() == null || pet.getPetId().isBlank()) {
            return EvaluationOutcome.success(); // invalid because petId missing
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            return EvaluationOutcome.success(); // invalid because name missing
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            return EvaluationOutcome.success(); // invalid because category missing
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            return EvaluationOutcome.success(); // invalid because status missing
        }
        // Also invalid if status is not one of AVAILABLE, PENDING, SOLD
        String status = pet.getStatus();
        if (!status.equals("AVAILABLE") && !status.equals("PENDING") && !status.equals("SOLD")) {
            return EvaluationOutcome.success(); // invalid due to wrong status
        }
        // If none of the invalid conditions met, fail to mark as invalid
        return EvaluationOutcome.fail("Pet is not invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
