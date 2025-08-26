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
public class ValidationSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationSuccessCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User entity = context.entity();

         if (entity == null) {
             logger.warn("ValidationSuccessCriterion invoked with null entity");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         StringBuilder problems = new StringBuilder();

         // id must be present and > 0
         if (entity.getId() == null || entity.getId() <= 0) {
             problems.append("id must be a positive integer; ");
         }

         // username required
         if (entity.getUsername() == null || entity.getUsername().isBlank()) {
             problems.append("username is required; ");
         }

         // name required
         if (entity.getName() == null || entity.getName().isBlank()) {
             problems.append("name is required; ");
         }

         // email required and basic format check
         if (entity.getEmail() == null || entity.getEmail().isBlank()) {
             problems.append("email is required; ");
         } else {
             String email = entity.getEmail();
             // basic email pattern
             if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                 problems.append("email format is invalid; ");
             }
         }

         // If address provided, ensure street and city are present
         User.Address addr = entity.getAddress();
         if (addr != null) {
             if (addr.getStreet() == null || addr.getStreet().isBlank()) {
                 problems.append("address.street is required when address is provided; ");
             }
             if (addr.getCity() == null || addr.getCity().isBlank()) {
                 problems.append("address.city is required when address is provided; ");
             }
             if (addr.getZipcode() != null && addr.getZipcode().isBlank()) {
                 problems.append("address.zipcode, if provided, must not be blank; ");
             }
         }

         // Ensure the entity is in VALIDATING state before marking success
         String status = entity.getProcessingStatus();
         if (status == null || status.isBlank()) {
             problems.append("processingStatus is required and must be VALIDATING; ");
         } else if (!"VALIDATING".equalsIgnoreCase(status)) {
             problems.append("processingStatus must be VALIDATING; ");
         }

         if (problems.length() > 0) {
             String message = problems.toString().trim();
             // Trim trailing semicolon if present
             if (message.endsWith(";")) {
                 message = message.substring(0, message.length() - 1).trim();
             }
             logger.debug("Validation failed for User id={} : {}", entity.getId(), message);
             return EvaluationOutcome.fail(message, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}