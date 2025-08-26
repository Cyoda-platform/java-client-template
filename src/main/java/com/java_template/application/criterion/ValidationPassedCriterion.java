package com.java_template.application.criterion;

import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
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

import java.util.List;

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
            .evaluateEntity(CommentAnalysisJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<CommentAnalysisJob> context) {
         CommentAnalysisJob entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("Entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate postId (must be present and not blank)
         String postId = entity.getPostId();
         if (postId == null || postId.isBlank()) {
             return EvaluationOutcome.fail("postId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate recipientEmail (must be present and have a basic valid format)
         String recipientEmail = entity.getRecipientEmail();
         if (recipientEmail == null || recipientEmail.isBlank()) {
             return EvaluationOutcome.fail("recipientEmail is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String email = recipientEmail.trim();
         // basic email format check: contain '@' and a dot after '@'
         int atIdx = email.indexOf('@');
         if (atIdx <= 0 || atIdx == email.length() - 1 || email.indexOf('.', atIdx) < atIdx + 2) {
             return EvaluationOutcome.fail("recipientEmail is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate requestedAt
         String requestedAt = entity.getRequestedAt();
         if (requestedAt == null || requestedAt.isBlank()) {
             return EvaluationOutcome.fail("requestedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate schedule
         String schedule = entity.getSchedule();
         if (schedule == null || schedule.isBlank()) {
             return EvaluationOutcome.fail("schedule is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate status
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate metricsConfig and metrics list
         CommentAnalysisJob.MetricsConfig metricsConfig = entity.getMetricsConfig();
         if (metricsConfig == null) {
             return EvaluationOutcome.fail("metricsConfig is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         List<String> metrics = metricsConfig.getMetrics();
         if (metrics == null || metrics.isEmpty()) {
             return EvaluationOutcome.fail("metrics list must contain at least one metric", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         for (String m : metrics) {
             if (m == null || m.isBlank()) {
                 return EvaluationOutcome.fail("metrics must not contain empty entries", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }
}