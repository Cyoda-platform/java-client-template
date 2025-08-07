package com.java_template.application.criterion;

import com.java_template.application.entity.Job;
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
public class JobFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request)
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         // Validate that the job status is 'completed' and the resultDetails indicate failure (e.g., contains 'error' or 'fail')
         if (job.getStatus() == null || !"completed".equalsIgnoreCase(job.getStatus())) {
            return EvaluationOutcome.fail("Job status must be 'completed'", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String details = job.getResultDetails();
         if (details == null || details.isBlank()) {
            return EvaluationOutcome.fail("Result details must be provided for a failed job", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String detailLower = details.toLowerCase();
         if (!(detailLower.contains("error") || detailLower.contains("fail"))) {
            return EvaluationOutcome.fail("Result details must indicate failure", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         return EvaluationOutcome.success();
    }
}
