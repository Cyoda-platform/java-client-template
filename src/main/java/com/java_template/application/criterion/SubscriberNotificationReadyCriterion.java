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
public class SubscriberNotificationReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SubscriberNotificationReadyCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("Subscriber entity is null in context");
             return EvaluationOutcome.fail("Subscriber entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String sid = subscriber.getSubscriberId();
         // Active check: must be present and true to be eligible for notifications
         Boolean active = subscriber.getActive();
         if (active == null || !active) {
             String msg = (sid != null ? "Subscriber [" + sid + "] is not active" : "Subscriber is not active");
             logger.debug(msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Contact address must be present
         String contactAddress = subscriber.getContactAddress();
         if (contactAddress == null || contactAddress.isBlank()) {
             String msg = (sid != null ? "Subscriber [" + sid + "] missing contactAddress" : "Subscriber missing contactAddress");
             logger.debug(msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Contact type must be present and one of expected values
         String contactType = subscriber.getContactType();
         if (contactType == null || contactType.isBlank()) {
             String msg = (sid != null ? "Subscriber [" + sid + "] missing contactType" : "Subscriber missing contactType");
             logger.debug(msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String ct = contactType.trim().toUpperCase();
         switch (ct) {
             case "EMAIL":
                 // basic email sanity check
                 if (!contactAddress.contains("@") || contactAddress.startsWith("@") || contactAddress.endsWith("@")) {
                     String msg = (sid != null ? "Subscriber [" + sid + "] has invalid email address" : "Subscriber has invalid email address");
                     logger.debug(msg + ": {}", contactAddress);
                     return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
                 break;
             case "WEBHOOK":
                 // webhook must be an http(s) URL
                 String lower = contactAddress.toLowerCase();
                 if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                     String msg = (sid != null ? "Subscriber [" + sid + "] has invalid webhook URL" : "Subscriber has invalid webhook URL");
                     logger.debug(msg + ": {}", contactAddress);
                     return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
                 break;
             case "OTHER":
                 // For OTHER, require non-blank address (already checked) but no further validation
                 break;
             default:
                 String msg = (sid != null ? "Subscriber [" + sid + "] has unsupported contactType: " + contactType : "Subscriber has unsupported contactType");
                 logger.debug(msg);
                 return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Filters are optional; no deep validation here. If provided ensure not blank
         String filters = subscriber.getFilters();
         if (filters != null && filters.isBlank()) {
             String msg = (sid != null ? "Subscriber [" + sid + "] has invalid filters value" : "Subscriber has invalid filters value");
             logger.debug(msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // lastNotifiedAt may be null; no check required here

         return EvaluationOutcome.success();
    }
}