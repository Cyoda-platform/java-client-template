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

@Component
public class AuthorSubmitsForReview implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AuthorSubmitsForReview(SerializerFactory serializerFactory) {
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
         Post post = context.entity();
         if (post == null) {
             return EvaluationOutcome.fail("Post entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: author must be present
         if (post.getAuthor_id() == null || post.getAuthor_id().isBlank()) {
             return EvaluationOutcome.fail("author_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: title must be present
         if (post.getTitle() == null || post.getTitle().isBlank()) {
             return EvaluationOutcome.fail("title is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: slug must be present
         if (post.getSlug() == null || post.getSlug().isBlank()) {
             return EvaluationOutcome.fail("slug is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: current_version_id must reference a PostVersion
         if (post.getCurrent_version_id() == null || post.getCurrent_version_id().isBlank()) {
             return EvaluationOutcome.fail("current_version_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: only posts in 'draft' may be submitted for review
         String status = post.getStatus();
         if (status == null || !status.equalsIgnoreCase("draft")) {
             return EvaluationOutcome.fail("post must be in 'draft' status to submit for review", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}