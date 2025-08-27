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

         // If already published, consider criterion satisfied
         if (entity.getPublishedAt() != null && !entity.getPublishedAt().isBlank()) {
             logger.debug("Post {} already has publishedAt={}; criterion satisfied", entity.getId(), entity.getPublishedAt());
             return EvaluationOutcome.success();
         }

         String publishDatetime = entity.getPublishDatetime();
         if (publishDatetime == null || publishDatetime.isBlank()) {
             logger.debug("Post {} missing publishDatetime", entity.getId());
             return EvaluationOutcome.fail("publishDatetime is required for scheduled publish", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Instant publishInstant;
         try {
             // Try parsing as Instant (expects zone/offset, e.g., trailing 'Z')
             publishInstant = Instant.parse(publishDatetime);
         } catch (DateTimeParseException e1) {
             try {
                 // Fallback to OffsetDateTime if Instant.parse fails (handles offsets)
                 publishInstant = OffsetDateTime.parse(publishDatetime).toInstant();
             } catch (DateTimeParseException e2) {
                 logger.warn("Post {} has invalid publishDatetime '{}'", entity.getId(), publishDatetime);
                 return EvaluationOutcome.fail("publishDatetime is not a valid ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         Instant now = Instant.now();
         if (now.isBefore(publishInstant)) {
             logger.debug("Post {} publishDatetime {} is in the future (now={})", entity.getId(), publishInstant, now);
             return EvaluationOutcome.fail("Publish time has not been reached", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         logger.debug("Post {} publishDatetime {} reached (now={}), criterion satisfied", entity.getId(), publishInstant, now);
         return EvaluationOutcome.success();
    }
}