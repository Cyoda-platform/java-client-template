package com.java_template.application.criterion;

import com.java_template.application.entity.owner.version_1.Owner;
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

import java.util.regex.Pattern;

@Component
public class EmailVerifiedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Simple, pragmatic email pattern (sufficient for verification-of-format)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public EmailVerifiedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Owner.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Use exact criterion name match (case-sensitive) as required
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Owner> context) {
         Owner owner = context.entity();
         if (owner == null) {
             logger.debug("EmailVerifiedCriterion: entity is null in context");
             return EvaluationOutcome.fail("Owner entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String email = owner.getEmail();
         if (email == null || email.isBlank()) {
             return EvaluationOutcome.fail("Email is required for verification", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String normalized = email.trim();
         if (!EMAIL_PATTERN.matcher(normalized).matches()) {
             return EvaluationOutcome.fail("Email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Additional basic sanity checks to reduce obvious bad data
         if (normalized.length() > 254) {
             return EvaluationOutcome.fail("Email exceeds maximum allowed length", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Local-part and domain basic length checks
         int atIndex = normalized.indexOf('@');
         if (atIndex <= 0 || atIndex == normalized.length() - 1) {
             return EvaluationOutcome.fail("Email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String local = normalized.substring(0, atIndex);
         String domain = normalized.substring(atIndex + 1);
         if (local.length() > 64) {
             return EvaluationOutcome.fail("Email local-part is too long", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (domain.length() > 253) {
             return EvaluationOutcome.fail("Email domain is too long", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Considered verified for the automated criterion if format and basic quality checks pass
         logger.debug("EmailVerifiedCriterion: email for owner {} passed verification checks", owner.getId());
         return EvaluationOutcome.success();
    }
}