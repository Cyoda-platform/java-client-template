package com.java_template.application.criterion;

import com.java_template.application.entity.Workflow;
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
public class ValidWorkflowCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public ValidWorkflowCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("ValidWorkflowCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(Workflow.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ValidWorkflowCriterion".equals(modelSpec.operationName()) &&
               "workflow".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Workflow workflow) {
        // Validate that all required fields are non-null and non-blank
        if (workflow.getName() == null || workflow.getName().isBlank()) {
            return EvaluationOutcome.fail("Workflow name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (workflow.getDescription() == null || workflow.getDescription().isBlank()) {
            return EvaluationOutcome.fail("Workflow description is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (workflow.getCreatedAt() == null || workflow.getCreatedAt().isBlank()) {
            return EvaluationOutcome.fail("Workflow creation timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (workflow.getStatus() == null || workflow.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Workflow status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Additional business rules can be added here
        // Example: status must be one of allowed values
        String status = workflow.getStatus();
        if (!status.equals("PENDING") && !status.equals("RUNNING") && !status.equals("COMPLETED") && !status.equals("FAILED")) {
            return EvaluationOutcome.fail("Invalid workflow status: " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
