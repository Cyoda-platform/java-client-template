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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(CommentAnalysisJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // CRITICAL: use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<CommentAnalysisJob> context) {
         CommentAnalysisJob entity = context.entity();

         // Null entity check
         if (entity == null) {
             logger.warn("ValidationFailedCriterion: entity is null in evaluation context");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // postId must be present and represent a positive integer (post identifier for external API)
         if (entity.getPostId() == null || entity.getPostId().isBlank()) {
             return EvaluationOutcome.fail("postId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         try {
             int pid = Integer.parseInt(entity.getPostId());
             if (pid <= 0) {
                 return EvaluationOutcome.fail("postId must be a positive integer", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (NumberFormatException nfe) {
             return EvaluationOutcome.fail("postId must be a numeric value", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // recipientEmail required and basic format validation
         if (entity.getRecipientEmail() == null || entity.getRecipientEmail().isBlank()) {
             return EvaluationOutcome.fail("recipientEmail is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String email = entity.getRecipientEmail();
         int atIdx = email.indexOf('@');
         if (atIdx <= 0 || atIdx != email.lastIndexOf('@') || atIdx == email.length() - 1) {
             return EvaluationOutcome.fail("recipientEmail format invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String domainPart = email.substring(atIdx + 1);
         if (!domainPart.contains(".") || domainPart.startsWith(".") || domainPart.endsWith(".")) {
             return EvaluationOutcome.fail("recipientEmail format invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // requestedAt required
         if (entity.getRequestedAt() == null || entity.getRequestedAt().isBlank()) {
             return EvaluationOutcome.fail("requestedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // schedule required (basic non-empty check)
         if (entity.getSchedule() == null || entity.getSchedule().isBlank()) {
             return EvaluationOutcome.fail("schedule is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // status required (basic non-empty check)
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate metricsConfig presence and its metrics list
         CommentAnalysisJob.MetricsConfig cfg = entity.getMetricsConfig();
         if (cfg == null) {
             return EvaluationOutcome.fail("metricsConfig is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         List<String> metrics = cfg.getMetrics();
         if (metrics == null || metrics.isEmpty()) {
             return EvaluationOutcome.fail("metricsConfig must contain at least one metric", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         for (String m : metrics) {
             if (m == null || m.isBlank()) {
                 return EvaluationOutcome.fail("metricsConfig contains empty metric", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}