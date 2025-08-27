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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ValidateSourceCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateSourceCriterion(SerializerFactory serializerFactory) {
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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
        IngestionJob entity = context.entity();
        if (entity == null) {
            return EvaluationOutcome.fail("IngestionJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate sourceUrl presence and format
        if (entity.getSourceUrl() == null || entity.getSourceUrl().isBlank()) {
            return EvaluationOutcome.fail("source_url is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        try {
            new URL(entity.getSourceUrl());
        } catch (MalformedURLException e) {
            return EvaluationOutcome.fail("source_url is not a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate dataFormats contains at least one supported format (JSON or XML)
        if (entity.getDataFormats() == null || entity.getDataFormats().isBlank()) {
            return EvaluationOutcome.fail("data_formats is required and must list at least one format", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        Set<String> providedFormats = Arrays.stream(entity.getDataFormats().split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toUpperCase)
            .collect(Collectors.toSet());
        Set<String> supported = new HashSet<>(Arrays.asList("JSON", "XML"));
        if (providedFormats.isEmpty()) {
            return EvaluationOutcome.fail("data_formats must contain at least one format (JSON,XML)", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (providedFormats.stream().noneMatch(supported::contains)) {
            return EvaluationOutcome.fail("data_formats must include at least one supported format: JSON or XML", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate timeWindowDays
        if (entity.getTimeWindowDays() == null) {
            return EvaluationOutcome.fail("time_window_days is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getTimeWindowDays() < 1) {
            return EvaluationOutcome.fail("time_window_days must be at least 1", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Business rule: limit the fetch window to a reasonable maximum to avoid excessive loads
        if (entity.getTimeWindowDays() > 30) {
            return EvaluationOutcome.fail("time_window_days cannot exceed 30 days", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate scheduleDay (must be a weekday name)
        if (entity.getScheduleDay() == null || entity.getScheduleDay().isBlank()) {
            return EvaluationOutcome.fail("schedule_day is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String day = entity.getScheduleDay().trim().toLowerCase();
        Set<String> validDays = new HashSet<>(Arrays.asList(
            "monday","tuesday","wednesday","thursday","friday","saturday","sunday"
        ));
        if (!validDays.contains(day)) {
            return EvaluationOutcome.fail("schedule_day must be a valid weekday name (e.g., Monday)", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate scheduleTime format HH:MM (24-hour)
        if (entity.getScheduleTime() == null || entity.getScheduleTime().isBlank()) {
            return EvaluationOutcome.fail("schedule_time is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String time = entity.getScheduleTime().trim();
        if (!time.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            return EvaluationOutcome.fail("schedule_time must be in HH:MM 24-hour format", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Business rule: Ingestion should be initiated from PENDING state for validation step
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!"PENDING".equals(entity.getStatus())) {
            return EvaluationOutcome.fail("IngestionJob must be in PENDING status to start validation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // createdBy presence (business/user metadata)
        if (entity.getCreatedBy() == null || entity.getCreatedBy().isBlank()) {
            return EvaluationOutcome.fail("created_by is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}