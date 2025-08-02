package com.java_template.application.criterion;

import com.java_template.application.entity.PetIngestionJob;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IsJobCompletedSuccessfulCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsJobCompletedSuccessfulCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EvaluationOutcome evaluate(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating IsJobCompletedSuccessfulCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetIngestionJob> context) {
        PetIngestionJob entity = context.entity();
        // Check if the job status is COMPLETED and there's no error message
        if ("COMPLETED".equals(entity.getStatus()) && (entity.getErrorMessage() == null || entity.getErrorMessage().isEmpty())) {
            return EvaluationOutcome.success();
        } else {
            return EvaluationOutcome.fail("Job is not in a successful COMPLETED state.", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
