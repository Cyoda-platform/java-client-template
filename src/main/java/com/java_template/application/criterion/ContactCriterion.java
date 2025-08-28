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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ContactCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Simple email and phone patterns suitable for basic validation (not exhaustive RFC)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?([0-9][0-9 \\-()]{5,20})$");

    public ContactCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Owner> context) {
         Owner entity = context.entity();

         if (entity == null) {
             logger.warn("ContactCriterion invoked with null entity");
             return EvaluationOutcome.fail("Owner entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String email = entity.getContactEmail();
         String phone = entity.getContactPhone();

         boolean hasEmail = email != null && !email.isBlank();
         boolean hasPhone = phone != null && !phone.isBlank();

         // If neither contact method present -> data quality failure
         if (!hasEmail && !hasPhone) {
             return EvaluationOutcome.fail("Missing contact information: both email and phone are empty",
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         List<String> validationErrors = new ArrayList<>();

         if (hasEmail) {
             if (!EMAIL_PATTERN.matcher(email).matches()) {
                 validationErrors.add("Invalid email format");
             }
         }

         if (hasPhone) {
             if (!PHONE_PATTERN.matcher(phone).matches()) {
                 validationErrors.add("Invalid phone format");
             }
         }

         if (!validationErrors.isEmpty()) {
             String message = String.join("; ", validationErrors);
             return EvaluationOutcome.fail(message, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // At least one valid contact method exists
         return EvaluationOutcome.success();
    }
}