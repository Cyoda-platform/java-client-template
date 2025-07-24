package com.java_template.application.criterion;

import com.java_template.application.entity.ScoreFetchJob;
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

import java.time.Instant;

@Component
public class IsFetchSuccessfulCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public IsFetchSuccessfulCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("IsFetchSuccessfulCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(ScoreFetchJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "IsFetchSuccessfulCriterion".equals(modelSpec.operationName()) &&
                "scoreFetchJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(ScoreFetchJob entity) {
        // Validation logic for fetch success: status must be COMPLETED and completedAt must be set
        if (entity.getStatus() == null) {
            return EvaluationOutcome.fail("Status is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!"COMPLETED".equalsIgnoreCase(entity.getStatus().name())) {
            return EvaluationOutcome.fail("Job status is not COMPLETED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (entity.getCompletedAt() == null) {
            return EvaluationOutcome.fail("Completion timestamp is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
