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

@Component
public class AnalysisCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AnalysisCompleteCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportJob> context) {
         ReportJob entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("ReportJob entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Job must be in ANALYZING state to evaluate analysis completion
         String status = entity.getStatus();
         if (status == null || !status.equalsIgnoreCase("ANALYZING")) {
             return EvaluationOutcome.fail("ReportJob is not in ANALYZING state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // requestedMetrics must be present to have something to analyze
         String requestedMetrics = entity.getRequestedMetrics();
         if (requestedMetrics == null || requestedMetrics.isBlank()) {
             return EvaluationOutcome.fail("requestedMetrics is required for analysis", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Analysis must have produced an artifact/location that the reporting step can consume.
         String reportLocation = entity.getReportLocation();
         if (reportLocation == null || reportLocation.isBlank()) {
             return EvaluationOutcome.fail("Analysis outputs are not available (reportLocation missing)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}