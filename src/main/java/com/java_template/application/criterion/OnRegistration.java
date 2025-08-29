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
public class OnRegistration implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OnRegistration(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(User.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "OnRegistration".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User user = context.entity();

         // userId must be present
         if (user.getUserId() == null || user.getUserId().isBlank()) {
            return EvaluationOutcome.fail("userId is required on registration", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // email must be present and contain '@'
         if (user.getEmail() == null || user.getEmail().isBlank()) {
             return EvaluationOutcome.fail("email is required on registration", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!user.getEmail().contains("@")) {
             return EvaluationOutcome.fail("email appears invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Profile with name should be supplied at registration
         if (user.getProfile() == null || user.getProfile().getName() == null || user.getProfile().getName().isBlank()) {
             return EvaluationOutcome.fail("profile.name is required on registration", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Newly registered users must not be already email-verified
         if (Boolean.TRUE.equals(user.getEmailVerified())) {
             return EvaluationOutcome.fail("email is already verified at registration", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Newly registered users should not own posts
         if (user.getOwnerOfPosts() != null && !user.getOwnerOfPosts().isEmpty()) {
             return EvaluationOutcome.fail("new user must not own posts at registration", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}