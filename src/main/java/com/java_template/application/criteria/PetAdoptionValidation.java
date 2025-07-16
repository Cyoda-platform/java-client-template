package com.java_template.application.criteria;

import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
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
public class PetAdoptionValidation implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetAdoptionValidation(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetAdoptionValidation initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Pet adoption validity for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Pet.class, this::validatePetAdoption)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .withErrorHandler((error, pet) -> {
                    logger.debug("Pet adoption validation failed for request: {}", request.getId(), error);
                    return ErrorInfo.validationError("Pet adoption validation failed: " + error.getMessage());
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetAdoptionValidation".equals(modelSpec.operationName()) &&
               "pet".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validatePetAdoption(Pet pet) {
        // Example validation: pet status must be 'adopted' for adoption to be valid
        if (pet.getStatus() != null && pet.getStatus().equalsIgnoreCase("adopted")) {
            return EvaluationOutcome.success();
        } else {
            return EvaluationOutcome.fail("Pet is not adopted yet", "businessRuleFailure");
        }
    }
}
