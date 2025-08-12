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
public class ValidateInputDataCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateInputDataCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request)
            .evaluateEntity(Workflow.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Workflow> context) {
         Workflow workflow = context.entity();
         // Business logic: Validate inputData is not null or blank
         if (workflow.getInputData() == null || workflow.getInputData().isBlank()) {
            return EvaluationOutcome.fail("Input data is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Additional example: status must be PENDING or VALIDATING to allow validation success
         if (workflow.getStatus() == null || 
             !(workflow.getStatus().equalsIgnoreCase("PENDING") || workflow.getStatus().equalsIgnoreCase("VALIDATING"))) {
            return EvaluationOutcome.fail("Workflow status must be PENDING or VALIDATING for validation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         return EvaluationOutcome.success();
    }
}
