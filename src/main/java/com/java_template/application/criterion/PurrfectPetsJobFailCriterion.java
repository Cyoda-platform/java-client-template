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
public class PurrfectPetsJobFailCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PurrfectPetsJobFailCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PurrfectPetsJobFailCriterion initialized with SerializerFactory");
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
        return "PurrfectPetsJobFailCriterion".equals(modelSpec.operationName()) &&
               "purrfectPetsJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PurrfectPetsJob entity) {
        // Fail if status is FAILED or if action is not recognized
        if (entity.getStatus() == null) {
            return EvaluationOutcome.fail("Status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getStatus() == PurrfectPetsJob.StatusEnum.FAILED) {
            return EvaluationOutcome.fail("Job is marked as FAILED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (entity.getAction() == null || entity.getAction().isBlank()) {
            return EvaluationOutcome.fail("Action is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Validate known actions
        String action = entity.getAction().toLowerCase();
        if (!("fetch".equals(action) || "enrich".equals(action) || "archive".equals(action))) {
            return EvaluationOutcome.fail("Action is not recognized", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
