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

import java.time.DateTimeException;
import java.time.Instant;

@Component
public class NowAtOrAfterPublishDatetime implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NowAtOrAfterPublishDatetime(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Post> context) {
         Post entity = context.entity();

         // Ensure publish_datetime is present
         String publishDatetime = entity.getPublish_datetime();
         if (publishDatetime == null || publishDatetime.isBlank()) {
             return EvaluationOutcome.fail("publish_datetime is required for scheduled publish", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Ensure the post is in a state that expects scheduled publication
         String status = entity.getStatus();
         if (status == null || !status.equalsIgnoreCase("scheduled")) {
             return EvaluationOutcome.fail("Post is not in 'scheduled' status", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Ensure a current version exists so publishing can build the bundle
         String currentVersionId = entity.getCurrent_version_id();
         if (currentVersionId == null || currentVersionId.isBlank()) {
             return EvaluationOutcome.fail("current_version_id is required to publish the post", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If already published, do not re-publish
         String publishedAt = entity.getPublished_at();
         if (publishedAt != null && !publishedAt.isBlank()) {
             return EvaluationOutcome.fail("Post is already published", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Parse publish_datetime and compare with now
         Instant publishInstant;
         try {
             publishInstant = Instant.parse(publishDatetime);
         } catch (DateTimeException ex) {
             logger.warn("Invalid publish_datetime format for post {}: {}", entity.getId(), publishDatetime);
             return EvaluationOutcome.fail("publish_datetime is not a valid ISO-8601 instant", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         Instant now = Instant.now();
         if (now.isBefore(publishInstant)) {
             return EvaluationOutcome.fail("Not time to publish yet", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Now is at or after publish time and other preconditions satisfied
         return EvaluationOutcome.success();
    }
}