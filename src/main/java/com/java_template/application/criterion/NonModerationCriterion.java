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

import java.util.Arrays;
import java.util.List;

@Component
public class NonModerationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Simple banned words/phrases list used by moderation rules
    private static final List<String> BANNED_TERMS = Arrays.asList(
        "spam", "viagra", "casino", "buy now", "free money", "click here", "subscribe"
    );

    // Minimum and maximum sensible word counts for non-moderated comments
    private static final int MIN_WORD_COUNT = 3;
    private static final int MAX_WORD_COUNT = 500;

    public NonModerationCriterion(SerializerFactory serializerFactory) {
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
         Comment comment = context.entity();

         if (comment == null) {
             logger.warn("NonModerationCriterion: comment entity is null");
             return EvaluationOutcome.fail("Comment entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String body = comment.getBody();
         if (body == null || body.isBlank()) {
             // Empty body is a validation/data quality issue and also a moderation trigger
             return EvaluationOutcome.fail("Comment body is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String normalizedBody = body.toLowerCase();

         // Check for banned terms
         for (String term : BANNED_TERMS) {
             if (normalizedBody.contains(term)) {
                 logger.info("NonModerationCriterion: banned term detected in comment id={}, term={}", comment.getId(), term);
                 return EvaluationOutcome.fail("Banned content detected", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Word count checks
         String[] words = normalizedBody.trim().split("\\s+");
         int wordCount = words.length;
         if (wordCount < MIN_WORD_COUNT) {
             logger.info("NonModerationCriterion: comment too short id={}, words={}", comment.getId(), wordCount);
             return EvaluationOutcome.fail("Comment too short", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         if (wordCount > MAX_WORD_COUNT) {
             logger.info("NonModerationCriterion: comment too long id={}, words={}", comment.getId(), wordCount);
             return EvaluationOutcome.fail("Comment too long", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If needed, additional simple heuristics could be added here (e.g., repeated URLs, excessive punctuation)

         return EvaluationOutcome.success();
    }
}