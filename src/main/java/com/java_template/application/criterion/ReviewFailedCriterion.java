package com.java_template.application.criterion;

import com.java_template.application.entity.task.version_1.Task;
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
public class ReviewFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReviewFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Task.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Task> context) {
        Task task = context.entity();
        try {
            if (task.getStatus() == null || !task.getStatus().equalsIgnoreCase("in_review")) {
                return EvaluationOutcome.fail("Task not in review", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (task.getMetadata() == null) return EvaluationOutcome.fail("No review metadata present", StandardEvalReasonCategories.VALIDATION_FAILURE);
            Object approved = task.getMetadata().get("reviewApproved");
            if (approved instanceof Boolean && !((Boolean) approved)) {
                return EvaluationOutcome.success();
            }
            return EvaluationOutcome.fail("Review did not fail", StandardEvalReasonCategories.VALIDATION_FAILURE);
        } catch (Exception e) {
            logger.error("Error in ReviewFailedCriterion: ", e);
            return EvaluationOutcome.fail("Error evaluating review failure: " + e.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
