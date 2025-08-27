package com.java_template.application.criterion;

import com.java_template.application.entity.user.version_1.User;
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
public class GdprErasureRequestedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public GdprErasureRequestedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(User.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User entity = context.entity();
         // Basic data quality checks using only available getters
         if (entity == null) {
             logger.debug("User entity is null in GdprErasureRequestedCriterion");
             return EvaluationOutcome.fail("Entity payload missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getUserId() == null) {
             return EvaluationOutcome.fail("userId is required for GDPR processing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getGdprState() == null || entity.getGdprState().isBlank()) {
             return EvaluationOutcome.fail("gdprState is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: this criterion should pass only when the user's GDPR state indicates erasure requested / pending
         // Per workflow specification the state for a requested erasure is "erased_pending"
         if ("erased_pending".equals(entity.getGdprState())) {
             logger.debug("GDPR erasure requested for user {}", entity.getUserId());
             return EvaluationOutcome.success();
         }

         // If gdprState is present but not indicating erasure, the criterion is not satisfied
         return EvaluationOutcome.fail("GDPR erasure not requested for this user", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}