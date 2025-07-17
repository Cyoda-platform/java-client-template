package com.java_template.application.criterion;

import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.function.Function;
import java.util.function.BiFunction;

@Component
public class PetValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetValidCriterion(CriterionSerializer serializerFactory) {
        this.serializer = serializerFactory;
        logger.info("PetValidCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Pet validity for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Pet.class, this::validatePet)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .withErrorHandler((error, pet) -> {
                    logger.debug("Pet validation failed for request: {}", request.getId(), error);
                    return ErrorInfo.validationError("Pet validation failed: " + error.getMessage());
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetValidCriterion".equals(modelSpec.operationName()) &&
                "pet".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validatePet(Pet pet) {
        if (pet.isValid()) {
            return EvaluationOutcome.success();
        } else {
            return EvaluationOutcome.fail("Pet is not valid", EvaluationOutcome.StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}