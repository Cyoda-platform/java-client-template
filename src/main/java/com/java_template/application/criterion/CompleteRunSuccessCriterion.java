package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class CompleteRunSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public CompleteRunSuccessCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
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
        if (latest.getStatus() == RunRecord.Status.SUCCEEDED) {
            return EvaluationOutcome.success();
        }
        // If metrics indicate no errors and some records processed consider success
        if (latest.getMetrics() != null) {
            Object errs = latest.getErrors();
            Number persisted = latest.getMetrics().get("recordsPersisted") instanceof Number ? (Number) latest.getMetrics().get("recordsPersisted") : null;
            if ((errs == null || (errs instanceof java.util.List && ((java.util.List) errs).isEmpty())) && persisted != null && persisted.intValue() > 0) {
                return EvaluationOutcome.success();
            }
        }
        return EvaluationOutcome.fail("Run not successful", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
