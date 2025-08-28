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

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class PastBehaviorCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private static final long MIN_DAYS_SINCE_REGISTRATION_FOR_TRUST = 90L;
    private static final int MIN_PRIOR_ADOPTIONS_FOR_TRUST = 1;

    public PastBehaviorCriterion(SerializerFactory serializerFactory) {
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

         if (user == null) {
             return EvaluationOutcome.fail("User entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic data quality checks
         if (user.getEmail() == null || user.getEmail().isBlank()) {
             return EvaluationOutcome.fail("Email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!user.getEmail().contains("@")) {
             return EvaluationOutcome.fail("Email appears malformed", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (user.getStatus() == null || user.getStatus().isBlank()) {
             return EvaluationOutcome.fail("User status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: suspended users cannot be trusted
         if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
             return EvaluationOutcome.fail("User is suspended", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Past behavior: user must have at least one prior adoption to be considered for trust elevation
         List<String> adopted = user.getAdoptedPetIds();
         if (adopted == null || adopted.isEmpty()) {
             return EvaluationOutcome.fail("No prior adoptions recorded", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         if (adopted.size() < MIN_PRIOR_ADOPTIONS_FOR_TRUST) {
             return EvaluationOutcome.fail("Insufficient prior adoptions", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Historical window: require user to be registered for a minimum period before trust elevation
         String registeredAt = user.getRegisteredAt();
         if (registeredAt == null || registeredAt.isBlank()) {
             return EvaluationOutcome.fail("registeredAt is required for past behavior evaluation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         try {
             OffsetDateTime reg = OffsetDateTime.parse(registeredAt);
             long days = ChronoUnit.DAYS.between(reg.toInstant(), OffsetDateTime.now().toInstant());
             if (days < MIN_DAYS_SINCE_REGISTRATION_FOR_TRUST) {
                 return EvaluationOutcome.fail("User registered less than " + MIN_DAYS_SINCE_REGISTRATION_FOR_TRUST + " days ago", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         } catch (DateTimeParseException ex) {
             logger.warn("Unable to parse registeredAt for user {}: {}", user.getUserId(), ex.getMessage());
             return EvaluationOutcome.fail("registeredAt timestamp is malformed", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}