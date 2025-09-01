package com.java_template.application.criterion;

import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
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
public class AggregationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AggregationFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(AdoptionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionJob> context) {
         AdoptionJob entity = context.entity();

         // Basic data quality checks
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("AdoptionJob.status is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If aggregation explicitly marked the job as FAILED, signal a business rule failure
         if ("FAILED".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("Aggregation step reported failure for AdoptionJob", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If aggregation completed but produced no results, mark data quality failure
         if ("COMPLETED".equalsIgnoreCase(entity.getStatus())) {
             Integer count = entity.getResultCount();
             boolean hasPreview = entity.getResultsPreview() != null && !entity.getResultsPreview().isEmpty();

             if ((count == null || count == 0) && !hasPreview) {
                 return EvaluationOutcome.fail("Aggregation completed but no results were produced", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             // If resultCount is present, it should be consistent with preview list size when preview is present
             if (count != null && hasPreview) {
                 int previewSize = entity.getResultsPreview().size();
                 if (count != previewSize) {
                     return EvaluationOutcome.fail("Inconsistent aggregation: resultCount does not match resultsPreview size", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Additional sanity: ensure resultsPreview entries are non-blank if present
         if (entity.getResultsPreview() != null) {
             for (String id : entity.getResultsPreview()) {
                 if (id == null || id.isBlank()) {
                     return EvaluationOutcome.fail("resultsPreview contains blank id", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
             }
         }

         return EvaluationOutcome.success();
    }
}