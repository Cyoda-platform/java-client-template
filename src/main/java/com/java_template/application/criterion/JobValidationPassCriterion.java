package com.java_template.application.criterion;

import com.java_template.application.entity.job.version_1.Job;
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
import org.springframework.scheduling.support.CronExpression;

import java.net.MalformedURLException;
import java.net.URL;

@Component
public class JobValidationPassCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobValidationPassCriterion(SerializerFactory serializerFactory) {
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
        if (job == null) {
            return EvaluationOutcome.fail("Job entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate sourceUrl
        String sourceUrl = null;
        try {
            sourceUrl = job.getSourceUrl();
        } catch (Exception e) {
            // entity may not have the getter; defensive
        }
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return EvaluationOutcome.fail("sourceUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        try {
            new URL(sourceUrl);
        } catch (MalformedURLException e) {
            return EvaluationOutcome.fail("sourceUrl is not a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate schedule - try parses as cron, otherwise accept as human-readable if non-empty
        String schedule = null;
        try {
            schedule = job.getSchedule();
        } catch (Exception e) {}
        if (schedule == null || schedule.isBlank()) {
            return EvaluationOutcome.fail("schedule is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        boolean cronValid = false;
        try {
            CronExpression.parse(schedule);
            cronValid = true;
        } catch (Exception ex) {
            // not a cron expression; allow human-readable schedules (heuristic: letters and spaces present)
            if (schedule.matches(".*[a-zA-Z].*")) {
                cronValid = true; // will be normalized later by scheduler
            }
        }
        if (!cronValid) {
            return EvaluationOutcome.fail("schedule is not a valid cron expression or recognized human schedule", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Config schema - best-effort: ensure config is present (can be empty)
        Object config = null;
        try {
            config = job.getConfig();
        } catch (Exception e) {}
        if (config == null) {
            // allow empty config but note as warning (do not fail)
            logger.warn("Job {} has no config object; proceeding with defaults", job);
        }

        return EvaluationOutcome.success();
    }
}
