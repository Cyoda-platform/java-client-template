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
public class IsVerifiedUserCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsVerifiedUserCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User user = context.entity();
         if (user == null) {
             logger.warn("IsVerifiedUserCriterion: user entity is null");
             return EvaluationOutcome.fail("User entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Primary check: explicit verified flag (preferred source)
         if (Boolean.TRUE.equals(user.getVerified())) {
             return EvaluationOutcome.success();
         }

         // Fallback checks / informative failures:
         // If email or contact missing, treat as data quality failure (can't verify)
         if (user.getEmail() == null || user.getEmail().isBlank()) {
             logger.debug("IsVerifiedUserCriterion: user {} missing email", user.getId());
             return EvaluationOutcome.fail("User not verified: missing email", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         // If verified flag false or null, report validation failure (user not verified)
         logger.debug("IsVerifiedUserCriterion: user {} not verified", user.getId());
         return EvaluationOutcome.fail("User not verified", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}