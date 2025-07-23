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
public class PetAvailabilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetAvailabilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetAvailabilityCriterion initialized with SerializerFactory");
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
        return "PetAvailabilityCriterion".equals(modelSpec.operationName()) &&
               "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Pet pet) {
        // Business logic: Validate pet availability
        if (pet == null) {
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (pet.getStatus() == null) {
            return EvaluationOutcome.fail("Pet status is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus().name())) {
            return EvaluationOutcome.fail("Pet is not available for adoption", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (pet.getName() == null || pet.getName().isBlank()) {
            return EvaluationOutcome.fail("Pet name is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            return EvaluationOutcome.fail("Pet category is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
