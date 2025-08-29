package com.java_template.application.criterion;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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
public class SourceAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SourceAvailableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(IngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob entity = context.entity();

         // Required: sourceUrl must be present
         if (entity.getSourceUrl() == null || entity.getSourceUrl().isBlank()) {
             return EvaluationOutcome.fail("sourceUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic URL quality check: must start with http:// or https://
         String src = entity.getSourceUrl().trim();
         if (!(src.startsWith("http://") || src.startsWith("https://"))) {
             return EvaluationOutcome.fail("sourceUrl must be a valid http(s) URL", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required: scheduleCron must be present
         if (entity.getScheduleCron() == null || entity.getScheduleCron().isBlank()) {
             return EvaluationOutcome.fail("scheduleCron is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic cron format check: commonly 5-7 whitespace separated fields
         String cron = entity.getScheduleCron().trim();
         if (!isCronLike(cron)) {
             return EvaluationOutcome.fail("scheduleCron appears invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If dataFormats provided, it must include at least JSON or XML
         if (entity.getDataFormats() != null && !entity.getDataFormats().isBlank()) {
             String formats = entity.getDataFormats().toUpperCase();
             if (!(formats.contains("JSON") || formats.contains("XML"))) {
                 return EvaluationOutcome.fail("dataFormats must include JSON or XML", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // If notifyEmail provided, perform a basic sanity check
         if (entity.getNotifyEmail() != null && !entity.getNotifyEmail().isBlank()) {
             String email = entity.getNotifyEmail().trim();
             if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
                 return EvaluationOutcome.fail("notifyEmail appears invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // All basic validations passed - assume source is available for downstream processors.
         return EvaluationOutcome.success();
    }

    private boolean isCronLike(String cron) {
        if (cron == null) return false;
        String[] parts = cron.trim().split("\\s+");
        // Accept common cron lengths: 5 (standard) or 6/7 (with seconds/year)
        if (parts.length < 5 || parts.length > 7) return false;
        // Ensure no empty parts
        for (String p : parts) {
            if (p == null || p.isBlank()) return false;
        }
        return true;
    }
}