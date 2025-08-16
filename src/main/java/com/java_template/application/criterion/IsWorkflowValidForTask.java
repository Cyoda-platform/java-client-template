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
public class IsWorkflowValidForTask implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsWorkflowValidForTask(SerializerFactory serializerFactory) {
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

        // Business logic: Check referenced Workflow exists and status allows new tasks
        if (task.getWorkflowTechnicalId() == null || task.getWorkflowTechnicalId().isBlank()) {
            return EvaluationOutcome.fail("Task must reference a Workflow", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (task.getStatus() == null || task.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Task status must be set", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // We simulate checking if the referenced Workflow exists and is READY
        // Since we do not have direct access to Workflow here, assume a method context.canAccessWorkflow(task.getWorkflowTechnicalId())
        // For demonstration, assume success

        if (!task.getStatus().equalsIgnoreCase("PENDING")) {
            return EvaluationOutcome.fail("Task status must be PENDING for a valid workflow", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
