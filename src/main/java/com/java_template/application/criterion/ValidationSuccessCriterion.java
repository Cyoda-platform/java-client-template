package com.java_template.application.criterion;

import com.java_template.application.entity.weeklyjob.version_1.WeeklyJob;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ValidationSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final Pattern TIME_PATTERN = Pattern.compile("^(?:[01]\\d|2[0-3]):[0-5]\\d$");
    private static final Set<String> VALID_WEEK_DAYS = Set.of(
        "MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"
    );
    private static final Set<String> KNOWN_STATUSES = Set.of(
        "PENDING","VALIDATING","SCHEDULED","RUNNING","COMPLETED","FAILED","NOTIFYING","SENT","ALERTING","REVIEW"
    );

    public ValidationSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(WeeklyJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // MUST use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<WeeklyJob> context) {
         WeeklyJob entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("WeeklyJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate name
         String name = entity.getName();
         if (name == null || name.isBlank()) {
             return EvaluationOutcome.fail("Job name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate recipients: must be present and each entry non-blank
         List<String> recipients = entity.getRecipients();
         if (recipients == null || recipients.isEmpty()) {
             return EvaluationOutcome.fail("Recipients list must be provided and contain at least one recipient", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         for (String r : recipients) {
             if (r == null || r.isBlank()) {
                 return EvaluationOutcome.fail("Recipients must not contain blank entries", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Validate runTime: required and must match HH:MM 24-hour
         String runTime = entity.getRunTime();
         if (runTime == null || runTime.isBlank()) {
             return EvaluationOutcome.fail("runTime is required and must be in HH:MM format", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!TIME_PATTERN.matcher(runTime).matches()) {
             return EvaluationOutcome.fail("runTime must be in 24-hour HH:MM format (e.g., 09:00)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate recurrenceDay: must be a weekday name
         String recurrenceDay = entity.getRecurrenceDay();
         if (recurrenceDay == null || recurrenceDay.isBlank()) {
             return EvaluationOutcome.fail("recurrenceDay is required and must specify a weekday (e.g., Wednesday)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!VALID_WEEK_DAYS.contains(recurrenceDay.trim().toUpperCase())) {
             return EvaluationOutcome.fail("recurrenceDay must be a valid weekday name (e.g., Monday, Tuesday, ...)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate timezone: required and must be a known ZoneId
         String timezone = entity.getTimezone();
         if (timezone == null || timezone.isBlank()) {
             return EvaluationOutcome.fail("timezone is required and must be a valid zone id (e.g., UTC)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         try {
             ZoneId.of(timezone);
         } catch (DateTimeException dte) {
             return EvaluationOutcome.fail("timezone is not a valid zone id: " + timezone, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate apiEndpoint: must be present and well-formed URL (http/https)
         String apiEndpoint = entity.getApiEndpoint();
         if (apiEndpoint == null || apiEndpoint.isBlank()) {
             return EvaluationOutcome.fail("apiEndpoint is required and must be a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         try {
             URI uri = new URI(apiEndpoint);
             String scheme = uri.getScheme();
             if (scheme == null || !(Objects.equals(scheme.toLowerCase(), "http") || Objects.equals(scheme.toLowerCase(), "https"))) {
                 return EvaluationOutcome.fail("apiEndpoint must use http or https scheme", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (uri.getHost() == null) {
                 return EvaluationOutcome.fail("apiEndpoint must contain a valid host", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (URISyntaxException use) {
             return EvaluationOutcome.fail("apiEndpoint is not a valid URI: " + apiEndpoint, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate status if present: should be a known lifecycle state
         String status = entity.getStatus();
         if (status != null && !status.isBlank()) {
             if (!KNOWN_STATUSES.contains(status.trim().toUpperCase())) {
                 return EvaluationOutcome.fail("status contains an unknown value: " + status, StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // All validations passed
         return EvaluationOutcome.success();
    }
}