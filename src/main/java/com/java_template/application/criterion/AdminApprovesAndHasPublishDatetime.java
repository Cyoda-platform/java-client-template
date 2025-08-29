package com.java_template.application.criterion;

import com.java_template.application.entity.post.version_1.Post;
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

import java.time.Instant;
import java.time.format.DateTimeParseException;

@Component
public class AdminApprovesAndHasPublishDatetime implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AdminApprovesAndHasPublishDatetime(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Post.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Post> context) {
         Post entity = context.entity();

         // The admin-approval + scheduling path requires the post to be currently in review.
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Post status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"in_review".equals(entity.getStatus())) {
             return EvaluationOutcome.fail("Admin approval can only be applied to posts in 'in_review' state",
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // A publish must reference a version
         if (entity.getCurrent_version_id() == null || entity.getCurrent_version_id().isBlank()) {
             return EvaluationOutcome.fail("current_version_id is required for approval", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // For the scheduling branch this criterion requires a publish_datetime
         if (entity.getPublish_datetime() == null || entity.getPublish_datetime().isBlank()) {
             return EvaluationOutcome.fail("publish_datetime is required to schedule publication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate that publish_datetime is a valid ISO-8601 instant and is in the future (scheduling)
         Instant publishInstant;
         try {
             publishInstant = Instant.parse(entity.getPublish_datetime());
         } catch (DateTimeParseException ex) {
             logger.debug("Invalid publish_datetime format for post {}: {}", entity.getId(), entity.getPublish_datetime(), ex);
             return EvaluationOutcome.fail("publish_datetime is not a valid ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         Instant now = Instant.now();
         if (!publishInstant.isAfter(now)) {
             // If the publish_datetime is now or in the past, this path expects an immediate publish flow instead.
             return EvaluationOutcome.fail("publish_datetime must be in the future to schedule publication", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All checks passed: admin approved AND has a valid future publish_datetime -> scheduled
         return EvaluationOutcome.success();
    }
}