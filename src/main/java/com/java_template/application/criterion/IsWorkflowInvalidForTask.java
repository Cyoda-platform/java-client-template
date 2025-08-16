package com.java_template.application.criterion;

import com.java_template.application.entity.Task;
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
public class IsWorkflowInvalidForTask implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsWorkflowInvalidForTask(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
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

        // Business logic: Check for invalid conditions
        if (task.getWorkflowTechnicalId() == null || task.getWorkflowTechnicalId().isBlank()) {
            return EvaluationOutcome.fail("Task must reference a Workflow", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (task.getStatus() == null || task.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Task status must be set", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Simulate invalid condition for demonstration:
        // For example, status is COMPLETED or unknown
        if (task.getStatus().equalsIgnoreCase("COMPLETED") || 
            !(task.getStatus().equalsIgnoreCase("PENDING") || task.getStatus().equalsIgnoreCase("IN_PROGRESS"))) {
            return EvaluationOutcome.fail("Task status is invalid for workflow processing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
