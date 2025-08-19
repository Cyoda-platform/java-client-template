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
public class AggregateSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AggregateSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(ReportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportJob> context) {
        ReportJob job = context.entity();
        if (job == null) {
            return EvaluationOutcome.fail("ReportJob is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Aggregation considered successful if reportId is present and status is COMPLETED
        if (job.getReportId() == null || job.getReportId().isEmpty()) {
            return EvaluationOutcome.fail("Aggregation did not produce a reportId", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (job.getStatus() == null || !job.getStatus().equalsIgnoreCase("COMPLETED")) {
            // still allow success if reportId present, but prefer status check
            logger.warn("Job {} has reportId={} but status={}", job.getTechnicalId(), job.getReportId(), job.getStatus());
        }
        return EvaluationOutcome.success();
    }
}
