package com.java_template.application.criterion;

import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
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
public class PetHasRequiredFieldsCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetHasRequiredFieldsCriterion(CriterionSerializer serializer) {
        this.serializer = serializer;
        logger.info("PetHasRequiredFieldsCriterion initialized with Serializer");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
                .evaluateEntity(Pet.class, this::validateRequiredFields)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    private EvaluationOutcome validateRequiredFields(Pet pet) {
        if (pet.getName() == null || pet.getName().isBlank()) {
            return EvaluationOutcome.fail("Pet name is required", StandardEvalReasonCategories.VALIDATION_FAILURE.getCode());
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            return EvaluationOutcome.fail("Pet category is required", StandardEvalReasonCategories.VALIDATION_FAILURE.getCode());
        }
        return EvaluationOutcome.success();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetHasRequiredFieldsCriterion".equals(modelSpec.operationName()) &&
                "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
