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
public class IsPetInvalid implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsPetInvalid(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {

        Pet pet = context.entity();

        // Inverse logic of IsPetValid: if any required field is missing or empty, or status is invalid, fail.
        if (pet.getName() == null || pet.getName().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (pet.getPhotoUrls() == null || pet.getPhotoUrls().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (pet.getTags() == null || pet.getTags().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            return EvaluationOutcome.success();
        }

        String status = pet.getStatus().toLowerCase();
        if (!status.equals("available") && !status.equals("pending") && !status.equals("sold")) {
            return EvaluationOutcome.success();
        }

        // If none of the above conditions met, then the pet is valid, so this criterion fails.
        return EvaluationOutcome.fail("Pet is valid, so this criterion fails", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
