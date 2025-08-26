package com.java_template.application.criterion;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
public class VerificationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public VerificationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Subscriber.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber subscriber = context.entity();

         if (subscriber == null) {
             return EvaluationOutcome.fail("subscriber entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic contact validation
         if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) {
             return EvaluationOutcome.fail("contactType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String contactTypeLower = subscriber.getContactType().trim().toLowerCase();
         if (!contactTypeLower.equals("email") && !contactTypeLower.equals("webhook") && !contactTypeLower.equals("sms")) {
             return EvaluationOutcome.fail("unsupported contactType: " + subscriber.getContactType(), StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (subscriber.getContactDetails() == null || subscriber.getContactDetails().isBlank()) {
             return EvaluationOutcome.fail("contactDetails is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Preferences, if present, must be well-formed
         Subscriber.Preferences prefs = subscriber.getPreferences();
         if (prefs != null) {
             if (prefs.getFrequency() == null || prefs.getFrequency().isBlank()) {
                 return EvaluationOutcome.fail("preferences.frequency is required when preferences are provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (prefs.getSpecies() == null || prefs.getSpecies().isEmpty()) {
                 return EvaluationOutcome.fail("preferences.species must contain at least one species when preferences are provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rule: subscriber must be verified to proceed
         if (!subscriber.isVerified()) {
             return EvaluationOutcome.fail("subscriber not verified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}