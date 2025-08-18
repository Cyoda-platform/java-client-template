package com.java_template.application.criterion;

import com.java_template.application.entity.retrievaljob.version_1.RetrievalJob;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RetrieveHNItemNotFoundCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public RetrieveHNItemNotFoundCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(RetrievalJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<RetrievalJob> context) {
        RetrievalJob job = context.entity();
        try {
            if ("NOT_FOUND".equalsIgnoreCase(job.getStatus())) {
                return EvaluationOutcome.success();
            }
            return EvaluationOutcome.fail("item found", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        } catch (Exception e) {
            logger.error("RetrieveHNItemNotFoundCriterion error: {}", e.getMessage(), e);
            return EvaluationOutcome.fail("evaluation error", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
