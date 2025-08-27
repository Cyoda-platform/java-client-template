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
public class EmailVerifiedAndConsentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailVerifiedAndConsentCriterion(SerializerFactory serializerFactory) {
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
        return modelSpec != null && className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User entity = context.entity();
         // Ensure entity present
         if (entity == null) {
             logger.warn("Validation failed: User entity is missing");
             return EvaluationOutcome.fail("User entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Email must be present and not blank
         if (entity.getEmail() == null || entity.getEmail().isBlank()) {
             logger.warn("Validation failed for user {}: email is required", entity.getUserId());
             return EvaluationOutcome.fail("Email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Email must be verified
         Boolean emailVerified = entity.getEmailVerified();
         if (emailVerified == null || !emailVerified) {
             logger.info("Validation failed for user {}: email not verified", entity.getUserId());
             return EvaluationOutcome.fail("Email not verified", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Marketing consent (double opt-in) must be granted. Use flattened marketing flag available on User.
         Boolean marketingEnabled = entity.getMarketingEnabled();
         if (marketingEnabled == null || !marketingEnabled) {
             logger.info("Validation failed for user {}: required marketing consent not granted", entity.getUserId());
             return EvaluationOutcome.fail("Required marketing consent not granted", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}