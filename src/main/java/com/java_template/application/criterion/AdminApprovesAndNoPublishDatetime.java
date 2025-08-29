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
public class AdminApprovesAndNoPublishDatetime implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AdminApprovesAndNoPublishDatetime(SerializerFactory serializerFactory) {
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
        return "AdminApprovesAndNoPublishDatetime".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Post> context) {
         Post post = context.entity();

         // Basic entity presence checks (use only existing getters)
         if (post == null) {
             return EvaluationOutcome.fail("Post entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Ensure the post has a current version to publish
         if (post.getCurrent_version_id() == null || post.getCurrent_version_id().isBlank()) {
             return EvaluationOutcome.fail("current_version_id is required to approve for immediate publish", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // The admin-approve-with-immediate-publish path requires the post to be in 'in_review'
         String status = post.getStatus();
         if (status == null || !status.equals("in_review")) {
             return EvaluationOutcome.fail("Post must be in 'in_review' state to be approved by admin", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // This specific criterion is for approvals where there is no scheduled publish time.
         String publishDatetime = post.getPublish_datetime();
         if (publishDatetime != null && !publishDatetime.isBlank()) {
             return EvaluationOutcome.fail("Post has a publish_datetime; use scheduled-approval flow instead", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Ensure required publishable metadata exists (title and slug are required for a publish)
         if (post.getTitle() == null || post.getTitle().isBlank()) {
             return EvaluationOutcome.fail("title is required for publishing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (post.getSlug() == null || post.getSlug().isBlank()) {
             return EvaluationOutcome.fail("slug is required for publishing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}