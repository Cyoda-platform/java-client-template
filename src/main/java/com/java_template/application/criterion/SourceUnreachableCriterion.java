package com.java_template.application.criterion;

import com.java_template.application.entity.importjob.version_1.ImportJob;
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
public class SourceUnreachableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SourceUnreachableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(ImportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use the exact criterion name (case-sensitive) as required.
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ImportJob> context) {
         ImportJob entity = context.entity();
         // Real validation using only available ImportJob properties:
         // - sourceUrl must be present
         // - sourceUrl must be a supported http(s) URL and not point to non-routable/local hosts
         if (entity == null) {
             return EvaluationOutcome.fail("ImportJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String sourceUrl = entity.getSourceUrl();
         if (sourceUrl == null || sourceUrl.isBlank()) {
             return EvaluationOutcome.fail("sourceUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String normalized = sourceUrl.trim().toLowerCase();
         // Basic scheme check
         if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
             return EvaluationOutcome.fail("Unsupported sourceUrl scheme, only http/https supported", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Reject clearly non-routable hosts commonly used in local testing
         if (normalized.contains("localhost") || normalized.contains("127.0.0.1") || normalized.startsWith("http://0.0.0.0")) {
             return EvaluationOutcome.fail("Source URL points to non-routable/local host", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Very short URLs are likely invalid
         if (normalized.length() < 12) {
             return EvaluationOutcome.fail("Invalid sourceUrl", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If job status indicates a previous failure consistent with unreachable source, surface as business rule failure
         String status = entity.getStatus();
         if (status != null && status.equalsIgnoreCase("FAILED") && entity.getFailedCount() != null && entity.getProcessedCount() != null && entity.getFailedCount() > 0 && entity.getProcessedCount() == 0) {
             return EvaluationOutcome.fail("Import job previously failed without processing records - possible unreachable source", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Passed heuristic checks — treat as reachable for the purpose of this criterion.
         return EvaluationOutcome.success();
    }
}