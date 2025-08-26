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

import java.util.ArrayList;
import java.util.List;

@Component
public class UserInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public UserInvalidCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("User entity is null in evaluation context");
             return EvaluationOutcome.fail("User entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         List<String> errors = new ArrayList<>();

         // Required fields
         if (user.getUsername() == null || user.getUsername().isBlank()) {
             errors.add("username is required");
         }
         if (user.getFullName() == null || user.getFullName().isBlank()) {
             errors.add("fullName is required");
         }
         if (user.getEmail() == null || user.getEmail().isBlank()) {
             errors.add("email is required");
         } else {
             String email = user.getEmail();
             // Simple RFC-lite email validation
             if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                 errors.add("email format is invalid");
             }
         }

         // Technical id if present must be positive
         if (user.getId() != null && user.getId() <= 0) {
             errors.add("id must be a positive integer when present");
         }

         // Source fetch timestamp should be present for ingested records
         if (user.getSourceFetchedAt() == null) {
             errors.add("sourceFetchedAt is required");
         }

         if (!errors.isEmpty()) {
             String msg = String.join("; ", errors);
             logger.debug("User validation failed: {}", msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // All checks passed => success
         return EvaluationOutcome.success();
    }
}