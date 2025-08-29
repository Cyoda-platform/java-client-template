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
public class AdminRequestsChanges implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AdminRequestsChanges(SerializerFactory serializerFactory) {
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
        // Must match the exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Post> context) {
         Post post = context.entity();

         // Ensure the post exists (defensive)
         if (post == null) {
             logger.warn("AdminRequestsChanges invoked with null Post entity");
             return EvaluationOutcome.fail("Post entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: admin can only request changes when post is in the 'in_review' state
         String status = post.getStatus();
         if (status == null || !status.equalsIgnoreCase("in_review")) {
             return EvaluationOutcome.fail("Post must be in 'in_review' to request changes", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Validation: title must be present
         String title = post.getTitle();
         if (title == null || title.isBlank()) {
             return EvaluationOutcome.fail("Post title is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validation: slug must be present
         String slug = post.getSlug();
         if (slug == null || slug.isBlank()) {
             return EvaluationOutcome.fail("Post slug is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validation: current_version_id should reference an existing version id for review context
         String currentVersionId = post.getCurrent_version_id();
         if (currentVersionId == null || currentVersionId.isBlank()) {
             return EvaluationOutcome.fail("current_version_id is required to request changes", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Data quality: author_id should be present for audit/tracking purposes
         String authorId = post.getAuthor_id();
         if (authorId == null || authorId.isBlank()) {
             return EvaluationOutcome.fail("author_id is missing for the post", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}