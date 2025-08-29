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
        // Predefined evaluation chain - business logic implemented in validateEntity
        return serializer.withRequest(request)
            .evaluateEntity(User.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match exact criterion name
        return "OnRegistration".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User user = context.entity();

         // Basic required fields for registration

         // userId must be present
         if (user.getUserId() == null || user.getUserId().isBlank()) {
            logger.debug("OnRegistration: missing userId");
            return EvaluationOutcome.fail("userId is required on registration", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // email must be present and contain '@'
         if (user.getEmail() == null || user.getEmail().isBlank()) {
             logger.debug("OnRegistration: missing email for user {}", user.getUserId());
             return EvaluationOutcome.fail("email is required on registration", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!user.getEmail().contains("@")) {
             logger.debug("OnRegistration: invalid email '{}' for user {}", user.getEmail(), user.getUserId());
             return EvaluationOutcome.fail("email appears invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Profile with name is required at registration (per UX requirement)
         if (user.getProfile() == null || user.getProfile().getName() == null || user.getProfile().getName().isBlank()) {
             logger.debug("OnRegistration: missing profile.name for user {}", user.getUserId());
             return EvaluationOutcome.fail("profile.name is required on registration", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If profile.locale is provided it must not be blank (avoid empty string)
         if (user.getProfile() != null && user.getProfile().getLocale() != null && user.getProfile().getLocale().isBlank()) {
             logger.debug("OnRegistration: profile.locale is blank for user {}", user.getUserId());
             return EvaluationOutcome.fail("profile.locale, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Newly registered users must not be already email-verified
         if (Boolean.TRUE.equals(user.getEmailVerified())) {
             logger.debug("OnRegistration: user {} already email verified", user.getUserId());
             return EvaluationOutcome.fail("email is already verified at registration", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Newly registered users should not own posts
         if (user.getOwnerOfPosts() != null && !user.getOwnerOfPosts().isEmpty()) {
             logger.debug("OnRegistration: user {} has ownerOfPosts populated at registration", user.getUserId());
             return EvaluationOutcome.fail("new user must not own posts at registration", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // GDPR state should not be pre-populated on fresh registration
         if (user.getGdprState() != null && !user.getGdprState().isBlank()) {
             logger.debug("OnRegistration: gdprState '{}' present for user {}", user.getGdprState(), user.getUserId());
             return EvaluationOutcome.fail("gdprState must not be set on initial registration", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         logger.debug("OnRegistration: validation succeeded for user {}", user.getUserId());
         return EvaluationOutcome.success();
    }
}