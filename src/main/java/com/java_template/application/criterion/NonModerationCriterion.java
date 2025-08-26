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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // Maximum allowed number of links in a comment before it's considered suspicious
    private static final int MAX_LINK_COUNT = 3;

    // Pattern to detect URLs in the text
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)|(www\\.\\S+)", Pattern.CASE_INSENSITIVE);

    public NonModerationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Comment.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return className.equals(modelSpec.operationName());
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
             logger.info("NonModerationCriterion: missing or blank body for comment id={}", comment.getId());
             return EvaluationOutcome.fail("Comment body is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String normalizedBody = body.trim().toLowerCase();

         // Check for banned terms (simple substring match)
         for (String term : BANNED_TERMS) {
             if (normalizedBody.contains(term)) {
                 logger.info("NonModerationCriterion: banned term detected in comment id={}, term={}", comment.getId(), term);
                 return EvaluationOutcome.fail("Banned content detected", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Word count checks
         String[] words = normalizedBody.replaceAll("\\s+", " ").trim().split(" ");
         int wordCount = 0;
         for (String w : words) {
             if (!w.isBlank()) wordCount++;
         }

         if (wordCount < MIN_WORD_COUNT) {
             logger.info("NonModerationCriterion: comment too short id={}, words={}", comment.getId(), wordCount);
             return EvaluationOutcome.fail("Comment too short", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         if (wordCount > MAX_WORD_COUNT) {
             logger.info("NonModerationCriterion: comment too long id={}, words={}", comment.getId(), wordCount);
             return EvaluationOutcome.fail("Comment too long", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Link count heuristic: too many links -> potential moderation needed
         Matcher urlMatcher = URL_PATTERN.matcher(body);
         int linkCount = 0;
         while (urlMatcher.find()) {
             linkCount++;
             if (linkCount > MAX_LINK_COUNT) break;
         }
         if (linkCount > MAX_LINK_COUNT) {
             logger.info("NonModerationCriterion: excessive links detected id={}, links={}", comment.getId(), linkCount);
             return EvaluationOutcome.fail("Excessive links detected", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Additional heuristic: repeated punctuation or non-alphanumeric spammy content
         long nonAlphaNumRatio = normalizedBody.chars().filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch)).count();
         double ratio = (double) nonAlphaNumRatio / Math.max(1, normalizedBody.length());
         if (ratio > 0.5) {
             logger.info("NonModerationCriterion: suspicious punctuation ratio id={}, ratio={}", comment.getId(), ratio);
             return EvaluationOutcome.fail("Suspicious content format", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If none of the moderation triggers matched, the comment passes non-moderation check
         return EvaluationOutcome.success();
    }
}