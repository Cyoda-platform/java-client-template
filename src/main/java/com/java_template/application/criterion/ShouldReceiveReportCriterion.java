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

import java.util.Map;

@Component
public class ShouldReceiveReportCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ShouldReceiveReportCriterion(SerializerFactory serializerFactory) {
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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber subscriber = context.entity();

         if (subscriber == null) {
             return EvaluationOutcome.fail("Subscriber entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Active flag must be present and true
         Boolean active = subscriber.getActive();
         if (active == null || !active) {
             return EvaluationOutcome.fail("Subscriber is not active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Opt-out: if optOutAt present and not blank, user explicitly opted out
         String optOutAt = subscriber.getOptOutAt();
         if (optOutAt != null && !optOutAt.isBlank()) {
             return EvaluationOutcome.fail("Subscriber has opted out", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Email must be present and reasonably well-formed
         String email = subscriber.getEmail();
         if (email == null || email.isBlank()) {
             return EvaluationOutcome.fail("Subscriber email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String trimmedEmail = email.trim();
         // basic validation: contains one '@', at least one '.' after '@', no spaces, local part non-empty
         if (trimmedEmail.contains(" ") || !trimmedEmail.contains("@")) {
             return EvaluationOutcome.fail("Subscriber email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         int atIdx = trimmedEmail.indexOf('@');
         if (atIdx <= 0 || atIdx != trimmedEmail.lastIndexOf('@')) {
             return EvaluationOutcome.fail("Subscriber email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         int dotAfterAt = trimmedEmail.indexOf('.', atIdx);
         if (dotAfterAt <= atIdx + 1 || dotAfterAt == trimmedEmail.length() - 1) {
             return EvaluationOutcome.fail("Subscriber email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Preferences should contain required keys for matching reports
         Map<String, String> preferences = subscriber.getPreferences();
         if (preferences == null || preferences.isEmpty()) {
             return EvaluationOutcome.fail("Subscriber preferences are missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         String frequency = preferences.get("frequency");
         String reportType = preferences.get("reportType");
         if (frequency == null || frequency.isBlank()) {
             return EvaluationOutcome.fail("Subscriber preference 'frequency' is missing or blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (reportType == null || reportType.isBlank()) {
             return EvaluationOutcome.fail("Subscriber preference 'reportType' is missing or blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed: subscriber is eligible to receive a report
         return EvaluationOutcome.success();
    }
}