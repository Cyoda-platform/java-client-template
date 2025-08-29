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
public class GdprTransferImmediate implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public GdprTransferImmediate(SerializerFactory serializerFactory) {
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
        // Must match exact criterion name
        return "GdprTransferImmediate".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User user = context.entity();
         if (user == null) {
             logger.warn("GdprTransferImmediate: received null user entity");
             return EvaluationOutcome.fail("User entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // userId is required
         if (user.getUserId() == null || user.getUserId().isBlank()) {
             logger.warn("GdprTransferImmediate: userId missing or blank for entity");
             return EvaluationOutcome.fail("userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // gdprState must be present to make a decision
         String gdprState = user.getGdprState();
         if (gdprState == null || gdprState.isBlank()) {
             logger.warn("GdprTransferImmediate: gdprState is missing for user {}", user.getUserId());
             return EvaluationOutcome.fail("gdprState is required for GDPR transfer", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If already transferred, this operation should not run again
         if ("transferred".equalsIgnoreCase(gdprState)) {
             logger.info("GdprTransferImmediate: user {} already in 'transferred' state", user.getUserId());
             return EvaluationOutcome.fail("User already transferred", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Only allow transfer when state is exactly erased_pending
         if (!"erased_pending".equals(gdprState)) {
             logger.info("GdprTransferImmediate: user {} in state '{}', expected 'erased_pending' to perform transfer", user.getUserId(), gdprState);
             return EvaluationOutcome.fail("GDPR transfer can only be performed when gdprState is 'erased_pending'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality: if ownerOfPosts present, ensure all references are non-blank
         if (user.getOwnerOfPosts() != null) {
             for (String ref : user.getOwnerOfPosts()) {
                 if (ref == null || ref.isBlank()) {
                     logger.warn("GdprTransferImmediate: user {} has invalid post reference in ownerOfPosts", user.getUserId());
                     return EvaluationOutcome.fail("ownerOfPosts contains invalid post reference", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         } else {
             // No posts to transfer is acceptable; log as info (attached as warning via serializer)
             logger.info("GdprTransferImmediate: user {} has no ownerOfPosts to transfer", user.getUserId());
         }

         // All checks passed — criterion indicates transfer should proceed
         logger.debug("GdprTransferImmediate: validation passed for user {}", user.getUserId());
         return EvaluationOutcome.success();
    }
}