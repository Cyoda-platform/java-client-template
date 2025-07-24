package com.java_template.application.criterion;

import com.java_template.application.entity.PurrfectPetsJob;
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
public class PurrfectPetsJobFetchActionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PurrfectPetsJobFetchActionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PurrfectPetsJobFetchActionCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(PurrfectPetsJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PurrfectPetsJobFetchActionCriterion".equals(modelSpec.operationName()) &&
               "purrfectPetsJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PurrfectPetsJob entity) {
        // Validate action is 'fetch' and petType is not blank
        if (entity.getAction() == null || entity.getAction().isBlank()) {
            return EvaluationOutcome.fail("Action is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!"fetch".equalsIgnoreCase(entity.getAction())) {
            return EvaluationOutcome.fail("Action must be 'fetch' to proceed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (entity.getPetType() == null || entity.getPetType().isBlank()) {
            return EvaluationOutcome.fail("Pet type is required for fetch action", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
