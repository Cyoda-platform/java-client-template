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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Subscriber.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber subscriber = context.entity();

         if (subscriber == null) {
             logger.warn("Validation failed: subscriber entity is null");
             return EvaluationOutcome.fail("subscriber entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required fields
         if (subscriber.getId() == null || subscriber.getId().isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (subscriber.getName() == null || subscriber.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
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

         // Contact details format checks by contactType
         String details = subscriber.getContactDetails().trim();
         switch (contactTypeLower) {
             case "email" -> {
                 if (!details.contains("@") || details.startsWith("@") || details.endsWith("@")) {
                     return EvaluationOutcome.fail("contactDetails must be a valid email address for contactType=email", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
             case "webhook" -> {
                 String dLower = details.toLowerCase();
                 if (!(dLower.startsWith("http://") || dLower.startsWith("https://"))) {
                     return EvaluationOutcome.fail("contactDetails must be a valid URL (http/https) for contactType=webhook", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
             case "sms" -> {
                 // basic phone validation: allow digits, spaces, dashes and leading '+', ensure reasonable length
                 String normalized = details.replaceAll("[\\s\\-()]", "");
                 if (normalized.startsWith("+")) normalized = normalized.substring(1);
                 if (!normalized.matches("\\d{7,15}")) {
                     return EvaluationOutcome.fail("contactDetails must be a valid phone number for contactType=sms", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
             default -> {
                 // already guarded above
             }
         }

         // Preferences validation (if provided)
         Subscriber.Preferences prefs = subscriber.getPreferences();
         if (prefs != null) {
             if (prefs.getFrequency() == null || prefs.getFrequency().isBlank()) {
                 return EvaluationOutcome.fail("preferences.frequency is required when preferences are provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             String freq = prefs.getFrequency().trim().toLowerCase();
             // Allowed frequencies from functional requirements: immediate, digest
             if (!freq.equals("immediate") && !freq.equals("digest")) {
                 return EvaluationOutcome.fail("preferences.frequency must be one of [immediate,digest]", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (prefs.getSpecies() == null || prefs.getSpecies().isEmpty()) {
                 return EvaluationOutcome.fail("preferences.species must contain at least one species when preferences are provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // Ensure species entries are non-blank
             for (String s : prefs.getSpecies()) {
                 if (s == null || s.isBlank()) {
                     return EvaluationOutcome.fail("preferences.species contains blank entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
             // tags may be null/empty; if present ensure entries non-blank
             if (prefs.getTags() != null) {
                 for (String t : prefs.getTags()) {
                     if (t == null || t.isBlank()) {
                         return EvaluationOutcome.fail("preferences.tags contains blank entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                     }
                 }
             }
         }

         // Business rule: subscriber must be verified to proceed with verification criterion outcome
         if (!subscriber.isVerified()) {
             return EvaluationOutcome.fail("subscriber not verified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}