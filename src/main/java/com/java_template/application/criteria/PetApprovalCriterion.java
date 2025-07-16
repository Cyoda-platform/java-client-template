package com.java_template.application.criteria;

import com.java_template.application.entity.pet.Pet;
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

import java.util.function.Function;

@Component
public class PetApprovalCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetApprovalCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetApprovalCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Pet approval validity for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Pet.class, this::validatePetApproval)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .withErrorHandler((error, pet) -> {
                    logger.debug("Pet approval validation failed for request: {}", request.getId(), error);
                    return ErrorInfo.validationError("Pet approval validation failed: " + error.getMessage());
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "pet".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validatePetApproval(Pet pet) {
        // Example validation: Pet age must be greater than 0 to be approved
        if (pet.getAge() != null && pet.getAge() > 0) {
            return EvaluationOutcome.success();
        } else {
            return EvaluationOutcome.failure("Pet age must be greater than 0 for approval");
        }
    }
}
