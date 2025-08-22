package com.java_template.application.criterion;

import com.java_template.application.entity.job.version_1.Job;
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
public class ValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job entity = context.entity();
         if (entity == null) {
             logger.warn("Job entity is null in ValidationFailedCriterion");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("Job.name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getScheduleType() == null || entity.getScheduleType().isBlank()) {
             return EvaluationOutcome.fail("Job.scheduleType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         } else {
             String st = entity.getScheduleType().trim();
             if (!(st.equalsIgnoreCase("one-time") || st.equalsIgnoreCase("recurring"))) {
                 return EvaluationOutcome.fail("Job.scheduleType must be 'one-time' or 'recurring'", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }
         if (entity.getScheduleSpec() == null || entity.getScheduleSpec().isBlank()) {
             return EvaluationOutcome.fail("Job.scheduleSpec is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSourceEndpoint() == null || entity.getSourceEndpoint().isBlank()) {
             return EvaluationOutcome.fail("Job.sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Job.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getEnabled() == null) {
             return EvaluationOutcome.fail("Job.enabled must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // All basic validation passed
         return EvaluationOutcome.success();
    }
}