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
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Component
public class HasPublishDatetime implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HasPublishDatetime(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
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
         String publishDatetime = entity.getPublishDatetime();
         String status = entity.getStatus();

         // If no publishDatetime provided
         if (publishDatetime == null || publishDatetime.isBlank()) {
             // publishDatetime is optional in general; however if the post is intended to be scheduled it must be present
             if (status != null && status.equalsIgnoreCase("scheduled")) {
                 logger.debug("Post {} is scheduled but missing publishDatetime", entity.getId());
                 return EvaluationOutcome.fail("publishDatetime is required for scheduling", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             // No datetime and not scheduled -> OK
             return EvaluationOutcome.success();
         }

         // Validate ISO-8601 parseability and obtain instant
         Instant parsedInstant;
         try {
             OffsetDateTime odt = OffsetDateTime.parse(publishDatetime);
             parsedInstant = odt.toInstant();
         } catch (DateTimeParseException e1) {
             try {
                 parsedInstant = Instant.parse(publishDatetime);
             } catch (DateTimeParseException e2) {
                 logger.debug("Post {} has invalid publishDatetime format: {}", entity.getId(), publishDatetime);
                 return EvaluationOutcome.fail("publishDatetime is not a valid ISO-8601 datetime", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Ensure logical correctness: publishDatetime should ordinarily be in the future for scheduled posts
         Instant now = Instant.now();
         if (!parsedInstant.isAfter(now)) {
             // If scheduled, this is a business-rule violation; otherwise treat as data-quality failure
             if (status != null && status.equalsIgnoreCase("scheduled")) {
                 logger.debug("Post {} has publishDatetime not in the future: {}", entity.getId(), publishDatetime);
                 return EvaluationOutcome.fail("publishDatetime must be in the future for scheduled posts", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             } else {
                 logger.debug("Post {} has publishDatetime in the past or now: {}", entity.getId(), publishDatetime);
                 return EvaluationOutcome.fail("publishDatetime is in the past or not in the future", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}