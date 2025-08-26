package com.java_template.application.criterion;

import com.java_template.application.entity.comment.version_1.Comment;
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

import java.util.Set;

@Component
public class ModerationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Simple banned terms/phrases - can be extended
    private static final Set<String> BANNED_TERMS = Set.of(
        "spam",
        "viagra",
        "buy now",
        "click here",
        "free money",
        "subscribe"
    );

    public ModerationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Comment.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Comment> context) {
         Comment entity = context.entity();
         if (entity == null) {
             logger.warn("ModerationCriterion invoked with null entity");
             return EvaluationOutcome.fail("Comment entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String body = entity.getBody();
         if (body == null || body.isBlank()) {
             return EvaluationOutcome.fail("Comment body is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String normalized = body.toLowerCase();

         // Check for banned terms/phrases
         for (String term : BANNED_TERMS) {
             if (normalized.contains(term)) {
                 logger.info("Comment flagged due to banned term '{}' in comment id={}", term, entity.getId());
                 return EvaluationOutcome.fail("Comment contains banned term: " + term, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Basic length/quality checks based on word count
         int wordCount = 0;
         String trimmed = body.trim();
         if (!trimmed.isEmpty()) {
             wordCount = trimmed.split("\\s+").length;
         }

         if (wordCount < 3) {
             logger.info("Comment flagged as too short ({} words) id={}", wordCount, entity.getId());
             return EvaluationOutcome.fail("Comment too short to be meaningful", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (wordCount > 1000) {
             logger.info("Comment flagged as too long ({} words) id={}", wordCount, entity.getId());
             return EvaluationOutcome.fail("Comment excessively long", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Check for suspicious number of links (simple heuristic)
         int linkCount = 0;
         String lower = normalized;
         int idx = lower.indexOf("http://");
         while (idx >= 0) {
             linkCount++;
             idx = lower.indexOf("http://", idx + 1);
         }
         idx = lower.indexOf("https://");
         while (idx >= 0) {
             linkCount++;
             idx = lower.indexOf("https://", idx + 1);
         }
         if (lower.contains("www.")) {
             // crude detection for additional links
             linkCount += lower.split("www\\.").length - 1;
         }
         if (linkCount > 3) {
             logger.info("Comment flagged for excessive links ({} links) id={}", linkCount, entity.getId());
             return EvaluationOutcome.fail("Comment contains excessive external links", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}