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
public class AdminArchives implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AdminArchives(SerializerFactory serializerFactory) {
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
         // Basic validation: id must be present
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("Post id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: only published posts can be archived by admin
         if (entity.getStatus() == null || !entity.getStatus().equalsIgnoreCase("published")) {
             return EvaluationOutcome.fail("Only posts with status 'published' can be archived", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality: published_at timestamp must be present for published posts
         if (entity.getPublished_at() == null || entity.getPublished_at().isBlank()) {
             return EvaluationOutcome.fail("published_at timestamp is required to archive a published post", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Data quality: current_version_id should reference the version that was published
         if (entity.getCurrent_version_id() == null || entity.getCurrent_version_id().isBlank()) {
             return EvaluationOutcome.fail("current_version_id is required for archived posts to reference their finalized content", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}