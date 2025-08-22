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
public class UserContactVerifiedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public UserContactVerifiedCriterion(SerializerFactory serializerFactory) {
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
             logger.debug("User entity is null in UserContactVerifiedCriterion");
             return EvaluationOutcome.fail("User entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String email = user.getEmail();
         String phone = user.getPhone();

         boolean emailValid = false;
         if (email != null) {
             String trimmed = email.trim();
             // basic sanity check: contains single @ and has non-empty local and domain parts
             int at = trimmed.indexOf('@');
             if (at > 0 && at == trimmed.lastIndexOf('@') && at < trimmed.length() - 1) {
                 // reject spaces
                 if (!trimmed.contains(" ")) {
                     emailValid = true;
                 }
             }
         }

         boolean phoneValid = false;
         if (phone != null) {
             String trimmed = phone.trim();
             // accept digits with optional leading +, length 7-15 digits
             if (trimmed.matches("^\\+?[0-9]{7,15}$")) {
                 phoneValid = true;
             }
         }

         if (emailValid || phoneValid) {
             logger.debug("User contact verified (emailValid={}, phoneValid={}) for userId={}", emailValid, phoneValid, user.getId());
             return EvaluationOutcome.success();
         }

         logger.debug("User contact not verified for userId={}; emailValid={}, phoneValid={}", user.getId(), emailValid, phoneValid);
         return EvaluationOutcome.fail("Neither email nor phone appears to be a valid verified contact", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}