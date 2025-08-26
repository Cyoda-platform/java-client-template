package com.java_template.application.criterion;

import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DeduplicationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DeduplicationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(CoverPhoto.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<CoverPhoto> context) {
         CoverPhoto entity = context.entity();

         // Basic required field validations (dedupe process requires these)
         if (entity.getSourceUrl() == null || entity.getSourceUrl().isBlank()) {
             logger.debug("DeduplicationCriterion: missing sourceUrl for entity id={}", entity.getId());
             return EvaluationOutcome.fail("sourceUrl is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getTitle() == null || entity.getTitle().isBlank()) {
             logger.debug("DeduplicationCriterion: missing title for entity id={}", entity.getId());
             return EvaluationOutcome.fail("title is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getThumbnailUrl() == null || entity.getThumbnailUrl().isBlank()) {
             logger.debug("DeduplicationCriterion: missing thumbnailUrl for entity id={}", entity.getId());
             return EvaluationOutcome.fail("thumbnailUrl is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getIngestionStatus() == null || entity.getIngestionStatus().isBlank()) {
             logger.debug("DeduplicationCriterion: missing ingestionStatus for entity id={}", entity.getId());
             return EvaluationOutcome.fail("ingestionStatus is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business/data-quality rules relevant to deduplication:
         // 1) Published photos must have a publishedDate
         if ("PUBLISHED".equalsIgnoreCase(entity.getIngestionStatus())) {
             if (entity.getPublishedDate() == null || entity.getPublishedDate().isBlank()) {
                 logger.debug("DeduplicationCriterion: published photo missing publishedDate id={}", entity.getId());
                 return EvaluationOutcome.fail("published photos must have publishedDate", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // 2) Ingested photos should not already have a publishedDate set
         if ("INGESTED".equalsIgnoreCase(entity.getIngestionStatus()) && entity.getPublishedDate() != null) {
             logger.debug("DeduplicationCriterion: INGESTED photo has publishedDate set id={}", entity.getId());
             return EvaluationOutcome.fail("ingested photos must not have publishedDate set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // 3) viewCount must be non-negative
         Integer vc = entity.getViewCount();
         if (vc != null && vc < 0) {
             logger.debug("DeduplicationCriterion: negative viewCount id={} value={}", entity.getId(), vc);
             return EvaluationOutcome.fail("viewCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // 4) thumbnailUrl should not be identical to sourceUrl (likely duplicate/quality issue)
         if (entity.getSourceUrl().equals(entity.getThumbnailUrl())) {
             logger.debug("DeduplicationCriterion: thumbnailUrl equals sourceUrl id={}", entity.getId());
             return EvaluationOutcome.fail("thumbnailUrl should differ from sourceUrl", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // 5) title and description should not be exact duplicates (poor data quality)
         if (entity.getDescription() != null && !entity.getDescription().isBlank() && entity.getTitle().equals(entity.getDescription())) {
             logger.debug("DeduplicationCriterion: title equals description id={}", entity.getId());
             return EvaluationOutcome.fail("title and description are identical", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // 6) tags should not contain duplicates (data quality)
         List<String> tags = entity.getTags();
         if (tags != null && !tags.isEmpty()) {
             Set<String> seen = new HashSet<>();
             for (String t : tags) {
                 if (t == null || t.isBlank()) {
                     logger.debug("DeduplicationCriterion: blank tag in id={}", entity.getId());
                     return EvaluationOutcome.fail("tags must not contain blank values", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
                 if (!seen.add(t)) {
                     logger.debug("DeduplicationCriterion: duplicate tag '{}' in id={}", t, entity.getId());
                     return EvaluationOutcome.fail("tags contain duplicate values", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // 7) comments: detect duplicate comments from same user in payload (business rule)
         List<CoverPhoto.Comment> comments = entity.getComments();
         if (comments != null && !comments.isEmpty()) {
             Set<String> commentSignatures = new HashSet<>();
             for (CoverPhoto.Comment c : comments) {
                 if (c == null) {
                     logger.debug("DeduplicationCriterion: null comment entry in id={}", entity.getId());
                     return EvaluationOutcome.fail("comments must not contain null entries", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
                 String uid = c.getUserId();
                 String text = c.getText();
                 if (uid == null || uid.isBlank() || text == null || text.isBlank() || c.getCreatedAt() == null || c.getCreatedAt().isBlank()) {
                     logger.debug("DeduplicationCriterion: invalid comment structure id={}", entity.getId());
                     return EvaluationOutcome.fail("each comment must have userId, text and createdAt", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
                 String sig = uid + "::" + text;
                 if (!commentSignatures.add(sig)) {
                     logger.debug("DeduplicationCriterion: duplicate comment by user '{}' in id={}", uid, entity.getId());
                     return EvaluationOutcome.fail("duplicate comments from same user detected", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             }
         }

        // If all checks pass, evaluation successful. Note: true cross-entity duplicate detection (existing records) is performed elsewhere by persistors;
        // this criterion enforces data quality and tells the pipeline whether this entity is fit for deduplication/publishing.
        return EvaluationOutcome.success();
    }
}