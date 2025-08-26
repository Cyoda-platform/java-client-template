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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(CoverPhoto.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<CoverPhoto> context) {
         CoverPhoto entity = context.entity();
         if (entity == null) {
             logger.warn("DeduplicationCriterion: entity is null in context");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic entity validity check using existing entity validation
         try {
             if (!entity.isValid()) {
                 logger.info("DeduplicationCriterion: entity failed isValid() checks (id/title/source/thumbnail/ingestion timestamps/tags/comments)");
                 return EvaluationOutcome.fail("Entity failed basic validation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } catch (Exception ex) {
             logger.error("DeduplicationCriterion: exception while validating entity.isValid()", ex);
             return EvaluationOutcome.fail("Entity validation error", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: Deduplication only runs for newly ingested items
         String status = entity.getIngestionStatus();
         if (status == null || !"INGESTED".equalsIgnoreCase(status.trim())) {
             logger.info("DeduplicationCriterion: entity ingestionStatus is not INGESTED (was: {})", status);
             return EvaluationOutcome.fail("Entity not in INGESTED state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Heuristic duplicate checks (use only entity properties available)
         // 1) Exact URL match between source and thumbnail is suspicious (likely duplicate record)
         String src = entity.getSourceUrl();
         String thumb = entity.getThumbnailUrl();
         if (src != null && thumb != null && src.equalsIgnoreCase(thumb)) {
             logger.info("DeduplicationCriterion: sourceUrl equals thumbnailUrl -> possible duplicate (id={})", entity.getId());
             return EvaluationOutcome.fail("Source and thumbnail URLs are identical — possible duplicate", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // 2) Title empty or extremely short is likely bad/duplicate metadata
         String title = entity.getTitle();
         if (title == null || title.trim().length() < 3) {
             logger.info("DeduplicationCriterion: title missing or too short (id={})", entity.getId());
             return EvaluationOutcome.fail("Title missing or too short", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // 3) If publishedDate is present but equals createdAt, it may indicate an import duplication - flag as warning-level data quality failure
         String createdAt = entity.getCreatedAt();
         String publishedDate = entity.getPublishedDate();
         if (publishedDate != null && createdAt != null && publishedDate.equals(createdAt)) {
             logger.info("DeduplicationCriterion: publishedDate equals createdAt (id={}) - potential duplicate/incorrect metadata", entity.getId());
             return EvaluationOutcome.fail("publishedDate equals createdAt — potential duplicate or incorrect metadata", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // 4) If comments list contains duplicates (same userId and createdAt), treat as data quality issue
         if (entity.getComments() != null) {
             for (int i = 0; i < entity.getComments().size(); i++) {
                 CoverPhoto.Comment ci = entity.getComments().get(i);
                 if (ci == null) {
                     logger.info("DeduplicationCriterion: null comment found (id={})", entity.getId());
                     return EvaluationOutcome.fail("Null comment entry found", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 for (int j = i + 1; j < entity.getComments().size(); j++) {
                     CoverPhoto.Comment cj = entity.getComments().get(j);
                     if (cj != null && ci.getUserId() != null && ci.getUserId().equals(cj.getUserId())
                         && ci.getCreatedAt() != null && ci.getCreatedAt().equals(cj.getCreatedAt())) {
                         logger.info("DeduplicationCriterion: duplicate comment detected for userId {} at {} (id={})", ci.getUserId(), ci.getCreatedAt(), entity.getId());
                         return EvaluationOutcome.fail("Duplicate comments detected", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                     }
                 }
             }
         }

         // If no heuristic flags found, consider the entity passed deduplication checks
         logger.debug("DeduplicationCriterion: entity passed deduplication checks (id={})", entity.getId());
         return EvaluationOutcome.success();
    }
}