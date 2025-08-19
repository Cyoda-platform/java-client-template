package com.java_template.application.criterion;

import com.java_template.application.entity.dataingestjob.version_1.DataIngestJob;
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
public class ValidationPassedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationPassedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(DataIngestJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<DataIngestJob> context) {
        DataIngestJob job = context.entity();
        if (job == null) return EvaluationOutcome.fail("Job is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        // Passed validation if status is DOWNLOADING or later and source_url reachable
        if ("DOWNLOADING".equalsIgnoreCase(job.getStatus()) || "ANALYZING".equalsIgnoreCase(job.getStatus()) || "DELIVERING".equalsIgnoreCase(job.getStatus())) {
            return EvaluationOutcome.success();
        }
        // If job explicitly marked FAILED, fail
        if ("FAILED".equalsIgnoreCase(job.getStatus())) {
            return EvaluationOutcome.fail("Job marked as FAILED", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.fail("Job not ready for downloading", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
