package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Component
public class ValidateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        // Defensive checks - rely on isValidEntity, but guard anyway
        if (job == null) {
            logger.warn("Job is null in processing context");
            return null;
        }

        StringBuilder summary = new StringBuilder();
        boolean valid = true;

        // Validate sourceEndpoint
        if (job.getSourceEndpoint() == null || job.getSourceEndpoint().isBlank()) {
            summary.append("sourceEndpoint is missing; ");
            valid = false;
        }

        // Validate scheduleSpec based on scheduleType
        String scheduleType = job.getScheduleType();
        String scheduleSpec = job.getScheduleSpec();

        if (scheduleType == null || scheduleType.isBlank()) {
            summary.append("scheduleType is missing; ");
            valid = false;
        } else {
            if ("recurring".equalsIgnoreCase(scheduleType) || "cron".equalsIgnoreCase(scheduleType)) {
                // Basic cron validation: must contain at least 5 space-separated fields
                if (scheduleSpec == null || scheduleSpec.isBlank()) {
                    summary.append("scheduleSpec is missing for recurring schedule; ");
                    valid = false;
                } else {
                    String[] parts = scheduleSpec.trim().split("\\s+");
                    if (parts.length < 5) {
                        summary.append("scheduleSpec does not appear to be a valid cron expression; ");
                        valid = false;
                    }
                }
            } else if ("one-time".equalsIgnoreCase(scheduleType) || "onetimer".equalsIgnoreCase(scheduleType)) {
                if (scheduleSpec == null || scheduleSpec.isBlank()) {
                    summary.append("scheduleSpec is missing for one-time schedule; ");
                    valid = false;
                } else {
                    boolean parsed = false;
                    try {
                        Instant.parse(scheduleSpec);
                        parsed = true;
                    } catch (DateTimeParseException e) {
                        // try LocalDateTime (ISO_LOCAL_DATE_TIME)
                        try {
                            LocalDateTime.parse(scheduleSpec);
                            parsed = true;
                        } catch (DateTimeParseException ex) {
                            // not parseable
                        }
                    }
                    if (!parsed) {
                        summary.append("scheduleSpec is not a valid ISO timestamp for one-time schedule; ");
                        valid = false;
                    }
                }
            } else {
                // Unknown scheduleType - treat as invalid
                summary.append("unsupported scheduleType: ").append(scheduleType).append("; ");
                valid = false;
            }
        }

        if (!valid) {
            job.setStatus("FAILED");
            String msg = summary.toString().trim();
            if (msg.isEmpty()) msg = "Validation failed";
            job.setLastResultSummary(msg);
            logger.info("Job validation failed (id={}): {}", job.getId(), msg);
        } else {
            job.setStatus("VALIDATED");
            job.setLastResultSummary("Validation passed");
            logger.info("Job validated successfully (id={})", job.getId());
        }

        return job;
    }
}