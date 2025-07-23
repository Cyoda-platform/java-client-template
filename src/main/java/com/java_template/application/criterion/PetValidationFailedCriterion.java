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
public class PetValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetValidationFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetValidationFailedCriterion initialized with SerializerFactory");
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
        return "PetValidationFailedCriterion".equals(modelSpec.operationName()) &&
                "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Pet entity) {
        // This criterion represents validation failure cases
        // Return failure if any mandatory field is missing or invalid
        if (entity.getPetId() == null || entity.getPetId().isBlank()) {
            return EvaluationOutcome.fail("petId is missing or blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getName() == null || entity.getName().isBlank()) {
            return EvaluationOutcome.fail("name is missing or blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getCategory() == null || entity.getCategory().isBlank()) {
            return EvaluationOutcome.fail("category is missing or blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("status is missing or blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Fail if status is not one of expected values
        String status = entity.getStatus().toLowerCase();
        if (!(status.equals("available") || status.equals("pending") || status.equals("sold"))) {
            return EvaluationOutcome.fail("status is invalid", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // If all validations pass, consider it success for this failure criterion
        return EvaluationOutcome.success();
    }
}
