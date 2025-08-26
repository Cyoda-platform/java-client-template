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

@Component
public class ContactInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String CRITERION_NAME = "ContactInvalidCriterion";

    public ContactInvalidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request)
            .evaluateEntity(Owner.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return CRITERION_NAME.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Owner> context) {
         Owner entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("Owner entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Owner.ContactInfo contact = entity.getContactInfo();
         if (contact == null) {
             return EvaluationOutcome.fail("contactInfo missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String email = contact.getEmail();
         if (email == null || email.isBlank()) {
             return EvaluationOutcome.fail("email missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic email format check: must contain '@' and a '.' after '@'
         int atIdx = email.indexOf('@');
         int lastDotIdx = email.lastIndexOf('.');
         if (atIdx <= 0 || lastDotIdx <= atIdx + 1 || lastDotIdx == email.length() - 1) {
             return EvaluationOutcome.fail("email format invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String phone = contact.getPhone();
         if (phone != null && phone.isBlank()) {
             return EvaluationOutcome.fail("phone present but blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Contact looks valid
         return EvaluationOutcome.success();
    }
}