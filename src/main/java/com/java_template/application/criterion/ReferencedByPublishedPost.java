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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Media.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Media> context) {
         Media entity = context.entity();
         if (entity == null) {
             logger.warn("ReferencedByPublishedPost: entity is null in evaluation context");
             return EvaluationOutcome.fail("Media entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic identity check
         if (entity.getMedia_id() == null || entity.getMedia_id().isBlank()) {
             return EvaluationOutcome.fail("media_id is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Status must be present to determine eligibility
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required to evaluate publishing eligibility", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If already published, criterion is satisfied
         if ("published".equalsIgnoreCase(status)) {
             return EvaluationOutcome.success();
         }

         // The intended transition is: processed -> published when referenced by a published Post.
         // We cannot inspect Posts from here (use only entity properties), so ensure Media is in 'processed' state
         // and has required delivery metadata (cdn_ref and created_at) that make it publishable.
         if (!"processed".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("Media is not in 'processed' state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality checks for processed media: cdn_ref and created_at must be present
         if (entity.getCdn_ref() == null || entity.getCdn_ref().isBlank()) {
             return EvaluationOutcome.fail("Processed media missing cdn_ref", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getCreated_at() == null || entity.getCreated_at().isBlank()) {
             return EvaluationOutcome.fail("Processed media missing created_at timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If all checks pass we consider the media eligible to be published when referenced by a published post.
         return EvaluationOutcome.success();
    }
}