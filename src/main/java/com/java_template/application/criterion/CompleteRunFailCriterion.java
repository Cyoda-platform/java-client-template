package com.java_template.application.criterion;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.job.version_1.RunRecord;
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
public class CompleteRunFailCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CompleteRunFailCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
        Job job = context.entity();
        if (job == null) return EvaluationOutcome.fail("Job null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        List<RunRecord> history = job.getRunHistory();
        if (history == null || history.isEmpty()) {
            return EvaluationOutcome.fail("No run history available", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        RunRecord latest = history.get(0);
        if (latest == null) {
            return EvaluationOutcome.fail("Latest run record is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (latest.getStatus() == RunRecord.Status.FAILED) {
            return EvaluationOutcome.success();
        }
        return EvaluationOutcome.fail("Run not failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
