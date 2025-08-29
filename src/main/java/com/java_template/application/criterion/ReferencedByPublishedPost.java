package com.java_template.application.criterion;

import com.java_template.application.entity.media.version_1.Media;
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
public class ReferencedByPublishedPost implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReferencedByPublishedPost(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Media.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Media> context) {
         Media entity = context.entity();
         if (entity == null) {
             logger.warn("ReferencedByPublishedPost: entity is null in evaluation context");
             return EvaluationOutcome.fail("Media entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic identity check
         if (entity.getMedia_id() == null || entity.getMedia_id().isBlank()) {
             logger.debug("ReferencedByPublishedPost: missing media_id");
             return EvaluationOutcome.fail("media_id is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Status must be present to determine eligibility
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             logger.debug("ReferencedByPublishedPost: missing status for media_id={}", entity.getMedia_id());
             return EvaluationOutcome.fail("status is required to evaluate publishing eligibility", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If already published, criterion is satisfied
         if ("published".equalsIgnoreCase(status)) {
             logger.debug("ReferencedByPublishedPost: media already published media_id={}", entity.getMedia_id());
             return EvaluationOutcome.success();
         }

         // The intended transition is: processed -> published when referenced by a published Post.
         // We cannot inspect Posts from here (use only entity properties), so ensure Media is in 'processed' state
         // and has required delivery metadata (cdn_ref and created_at) that make it publishable.
         if (!"processed".equalsIgnoreCase(status)) {
             logger.debug("ReferencedByPublishedPost: media not in processed state (media_id={} status={})", entity.getMedia_id(), status);
             return EvaluationOutcome.fail("Media is not in 'processed' state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality checks for processed media: cdn_ref and created_at must be present
         if (entity.getCdn_ref() == null || entity.getCdn_ref().isBlank()) {
             logger.debug("ReferencedByPublishedPost: processed media missing cdn_ref media_id={}", entity.getMedia_id());
             return EvaluationOutcome.fail("Processed media missing cdn_ref", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getCreated_at() == null || entity.getCreated_at().isBlank()) {
             logger.debug("ReferencedByPublishedPost: processed media missing created_at media_id={}", entity.getMedia_id());
             return EvaluationOutcome.fail("Processed media missing created_at timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Owner must be present (foreign key reference)
         if (entity.getOwner_id() == null || entity.getOwner_id().isBlank()) {
             logger.debug("ReferencedByPublishedPost: media missing owner_id media_id={}", entity.getMedia_id());
             return EvaluationOutcome.fail("owner_id is required for media", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Validate versions metadata if present: at least one version with identifiers and filename
         if (entity.getVersions() == null || entity.getVersions().isEmpty()) {
             logger.debug("ReferencedByPublishedPost: processed media has no versions media_id={}", entity.getMedia_id());
             return EvaluationOutcome.fail("Processed media must contain at least one version", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         boolean foundValidVersion = false;
         for (Media.MediaVersion v : entity.getVersions()) {
             if (v != null && v.getVersion_id() != null && !v.getVersion_id().isBlank()
                     && v.getFilename() != null && !v.getFilename().isBlank()) {
                 foundValidVersion = true;
                 break;
             }
         }
         if (!foundValidVersion) {
             logger.debug("ReferencedByPublishedPost: no valid media versions for media_id={}", entity.getMedia_id());
             return EvaluationOutcome.fail("Processed media lacks valid version metadata", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If all checks pass we consider the media eligible to be published when referenced by a published post.
         logger.debug("ReferencedByPublishedPost: media eligible media_id={}", entity.getMedia_id());
         return EvaluationOutcome.success();
    }
}