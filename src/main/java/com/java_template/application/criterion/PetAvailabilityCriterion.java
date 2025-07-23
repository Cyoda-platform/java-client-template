package com.java_template.application.criterion;

import com.java_template.application.entity.PetAdoptionJob;
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
            .evaluateEntity(PetAdoptionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetAvailabilityCriterion".equals(modelSpec.operationName()) &&
               "petAdoptionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetAdoptionJob entity) {
        // Validation logic: Check that the petId is not null or blank and simulate pet availability check
        if (entity.getPetId() == null || entity.getPetId().isBlank()) {
            return EvaluationOutcome.fail("Pet ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // In real scenario, would check external Pet entity status (e.g., PetStatusEnum.AVAILABLE)
        // Here, we simulate the pet is available if petId is set (example logic)
        // For demonstration, assume pet is available if petId starts with 'p'
        if (!entity.getPetId().startsWith("p")) {
            return EvaluationOutcome.fail("Pet is not available", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
