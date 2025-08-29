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
public class EmailVerifiedAndConsent implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailVerifiedAndConsent(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User user = context.entity();

         // Basic data quality checks
         if (user == null) {
             return EvaluationOutcome.fail("User entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (user.getUserId() == null || user.getUserId().isBlank()) {
             return EvaluationOutcome.fail("userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (user.getEmail() == null || user.getEmail().isBlank()) {
             return EvaluationOutcome.fail("email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: email must be verified
         Boolean emailVerified = user.getEmailVerified();
         if (emailVerified == null || !emailVerified) {
             return EvaluationOutcome.fail("email is not verified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Business rule: consent (marketing flag) must be enabled (represents double opt-in consent)
         Boolean marketingEnabled = user.getMarketingEnabled();
         if (marketingEnabled == null || !marketingEnabled) {
             return EvaluationOutcome.fail("marketing consent not granted", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}