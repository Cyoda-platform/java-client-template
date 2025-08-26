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
public class ValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailedCriterion(SerializerFactory serializerFactory) {
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
             logger.debug("ValidationFailedCriterion: user entity is null");
             return EvaluationOutcome.fail("User entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (user.getId() == null || user.getId() <= 0) {
             return EvaluationOutcome.fail("id is required and must be a positive integer", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (user.getUsername() == null || user.getUsername().isBlank()) {
             return EvaluationOutcome.fail("username is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (user.getName() == null || user.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (user.getEmail() == null || user.getEmail().isBlank()) {
             return EvaluationOutcome.fail("email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // basic email format sanity check (simple)
         String email = user.getEmail();
         if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
             return EvaluationOutcome.fail("email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If address is present, validate core address fields as per entity contract
         User.Address addr = user.getAddress();
         if (addr != null) {
             if (addr.getStreet() == null || addr.getStreet().isBlank()) {
                 return EvaluationOutcome.fail("address.street is required when address is provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (addr.getCity() == null || addr.getCity().isBlank()) {
                 return EvaluationOutcome.fail("address.city is required when address is provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (addr.getZipcode() != null && addr.getZipcode().isBlank()) {
                 return EvaluationOutcome.fail("address.zipcode, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // All validation checks passed
         return EvaluationOutcome.success();
    }
}