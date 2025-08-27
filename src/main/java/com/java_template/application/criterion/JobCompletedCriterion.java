package com.java_template.application.criterion;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
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

import java.util.List;

@Component
public class JobCompletedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobCompletedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(ReportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return "JobCompletedCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportJob> context) {
         ReportJob entity = context.entity();

         if (entity == null) {
             logger.warn("ReportJob entity is null for JobCompletedCriterion");
             return EvaluationOutcome.fail("ReportJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || !status.equals("COMPLETED")) {
             return EvaluationOutcome.fail("ReportJob is not in COMPLETED state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // When status is COMPLETED, ensure essential output/metadata are present
         if (entity.getTitle() == null || entity.getTitle().isBlank()) {
             return EvaluationOutcome.fail("Completed job missing title", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getRequestedBy() == null || entity.getRequestedBy().isBlank()) {
             return EvaluationOutcome.fail("Completed job missing requestedBy", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("Completed job missing createdAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         List<String> exportFormats = entity.getExportFormats();
         if (exportFormats == null || exportFormats.isEmpty()) {
             return EvaluationOutcome.fail("Completed job must specify at least one export format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         // notify if present must not be blank (ReportJob.isValid follows same rule but re-check here)
         if (entity.getNotify() != null && entity.getNotify().isBlank()) {
             return EvaluationOutcome.fail("notify is provided but blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}