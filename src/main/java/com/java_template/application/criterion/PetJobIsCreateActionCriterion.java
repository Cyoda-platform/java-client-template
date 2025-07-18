package com.java_template.application.criterion;

import com.java_template.application.entity.PetJob;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PetJobIsCreateActionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetJobIsCreateActionCriterion(CriterionSerializer.SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetJobIsCreateActionCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
                .evaluateEntity(PetJob.class, this::applyValidation1)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    private EvaluationOutcome applyValidation1(PetJob petJob) {
        if (petJob.getAction() == null || !"CREATE".equalsIgnoreCase(petJob.getAction())) {
            return EvaluationOutcome.fail("Action is not CREATE", com.java_template.common.serializer.StandardEvalReasonCategories.VALIDATION_FAILURE.getCode());
        }
        return EvaluationOutcome.success();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobIsCreateActionCriterion".equals(modelSpec.operationName()) &&
                "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
