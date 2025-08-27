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
public class ReferencedByPublishedPostCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReferencedByPublishedPostCriterion(SerializerFactory serializerFactory) {
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
        // Must match exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Media> context) {
         Media entity = context.entity();
         if (entity == null) {
             logger.debug("ReferencedByPublishedPostCriterion: entity is null in context");
             return EvaluationOutcome.fail("Entity payload missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         // If media is not in published state, criterion is not applicable — succeed
         if (status == null || !status.equalsIgnoreCase("published")) {
             return EvaluationOutcome.success();
         }

         // For published media, ensure essential publish metadata is present
         if (entity.getMedia_id() == null || entity.getMedia_id().isBlank()) {
             logger.debug("Published media missing media_id: {}", entity);
             return EvaluationOutcome.fail("Published media must have media_id", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCdn_ref() == null || entity.getCdn_ref().isBlank()) {
             logger.debug("Published media missing cdn_ref for media_id={}", entity.getMedia_id());
             return EvaluationOutcome.fail("Published media must have cdn_ref", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getCreated_at() == null || entity.getCreated_at().isBlank()) {
             logger.debug("Published media missing created_at for media_id={}", entity.getMedia_id());
             return EvaluationOutcome.fail("Published media must have created_at timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}