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
public class AdminSuspendRequestedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AdminSuspendRequestedCriterion(SerializerFactory serializerFactory) {
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
        if (modelSpec == null || modelSpec.operationName() == null) {
            return false;
        }
        // MUST use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User user = context.entity();
         if (user == null) {
             return EvaluationOutcome.fail("User entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required technical identifier
         if (user.getUserId() == null) {
             return EvaluationOutcome.fail("userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Email must be present and non-blank
         String email = user.getEmail();
         if (email == null || email.isBlank()) {
             return EvaluationOutcome.fail("email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If profile present, ensure name is present (data quality)
         User.Profile profile = user.getProfile();
         if (profile != null) {
             String name = profile.getName();
             if (name == null || name.isBlank()) {
                 return EvaluationOutcome.fail("profile.name is required when profile is present", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rules that block suspension:
         String gdpr = user.getGdprState();
         if (gdpr != null) {
             if ("erased_pending".equalsIgnoreCase(gdpr)) {
                 return EvaluationOutcome.fail("user erasure is pending; cannot suspend", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             if ("transferred".equalsIgnoreCase(gdpr)) {
                 return EvaluationOutcome.fail("user already transferred for GDPR; cannot suspend", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Prefer suspending active users only: require email verified flag true
         Boolean emailVerified = user.getEmailVerified();
         if (emailVerified == null || !emailVerified) {
             return EvaluationOutcome.fail("email must be verified before admin suspension", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}