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
public class HasNoPublishDatetime implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HasNoPublishDatetime(SerializerFactory serializerFactory) {
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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Post> context) {
         Post entity = context.entity();
         if (entity == null) {
             logger.warn("HasNoPublishDatetime: entity is null in evaluation context");
             return EvaluationOutcome.fail("Entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String publishDatetime = entity.getPublishDatetime();
         // Consider both null and blank as "not set". Success when there is no publishDatetime set (i.e., not scheduled)
         if (publishDatetime == null || publishDatetime.isBlank()) {
             return EvaluationOutcome.success();
         }

         // If publishDatetime is present (non-null and non-blank), this criterion fails.
         // Use BUSINESS_RULE_FAILURE since this represents a business rule: the post must not be scheduled for this operation.
         return EvaluationOutcome.fail("publishDatetime must not be set for this operation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}