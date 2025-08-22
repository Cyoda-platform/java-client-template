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
public class ValidationPassedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationPassedCriterion(SerializerFactory serializerFactory) {
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
         Job job = context.entity();
         if (job == null) {
             logger.warn("ValidationPassedCriterion: incoming Job entity is null");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (job.getName() == null || job.getName().isBlank()) {
             return EvaluationOutcome.fail("Job.name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (job.getScheduleSpec() == null || job.getScheduleSpec().isBlank()) {
             return EvaluationOutcome.fail("Job.scheduleSpec is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (job.getScheduleType() == null || job.getScheduleType().isBlank()) {
             return EvaluationOutcome.fail("Job.scheduleType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Enforce expected scheduleType values per functional requirements
         String st = job.getScheduleType().trim().toLowerCase();
         if (!st.equals("one-time") && !st.equals("recurring")) {
             return EvaluationOutcome.fail("Job.scheduleType must be either 'one-time' or 'recurring'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if (job.getSourceEndpoint() == null || job.getSourceEndpoint().isBlank()) {
             return EvaluationOutcome.fail("Job.sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Job.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (job.getEnabled() == null) {
             return EvaluationOutcome.fail("Job.enabled flag must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Optional fields are lastRunTimestamp and lastResultSummary; no validation required here.

         return EvaluationOutcome.success();
    }
}