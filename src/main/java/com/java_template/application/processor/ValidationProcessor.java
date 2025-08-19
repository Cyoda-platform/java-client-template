package com.java_template.application.processor;

import com.java_template.application.entity.extractionjob.version_1.ExtractionJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;

@Component
public class ValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ExtractionJob validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ExtractionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ExtractionJob entity) {
        return entity != null;
    }

    private ExtractionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ExtractionJob> context) {
        ExtractionJob entity = context.entity();
        // Basic validation rules derived from functional requirements
        try {
            if (entity.getSchedule() == null || entity.getSchedule().trim().isEmpty()) {
                logger.warn("Validation failed: schedule is missing for jobId={}", entity.getJobId());
                if (hasSetter(entity, "setStatus")) entity.setStatus("FAILED");
                if (hasSetter(entity, "setFailureReason")) entity.setFailureReason("INVALID_SCHEDULE");
                return entity;
            }

            if (entity.getSourceUrl() == null || entity.getSourceUrl().trim().isEmpty()) {
                logger.warn("Validation failed: sourceUrl is missing for jobId={}", entity.getJobId());
                if (hasSetter(entity, "setStatus")) entity.setStatus("FAILED");
                if (hasSetter(entity, "setFailureReason")) entity.setFailureReason("MISSING_SOURCE_URL");
                return entity;
            }

            // validate URL format
            try {
                new URL(entity.getSourceUrl());
            } catch (MalformedURLException e) {
                logger.warn("Validation failed: sourceUrl not a valid URL for jobId={}", entity.getJobId());
                if (hasSetter(entity, "setStatus")) entity.setStatus("FAILED");
                if (hasSetter(entity, "setFailureReason")) entity.setFailureReason("INVALID_SOURCE_URL");
                return entity;
            }

            // recipients should be present
            if (entity.getRecipients() == null || entity.getRecipients().isEmpty()) {
                logger.warn("Validation warning: recipients empty for jobId={}", entity.getJobId());
                // Non-fatal: allow scheduled but warn
                if (hasSetter(entity, "setStatus")) entity.setStatus("SCHEDULED");
                return entity;
            }

            // All good
            logger.info("Validation passed for jobId={}", entity.getJobId());
            if (hasSetter(entity, "setStatus")) entity.setStatus("VALIDATING");
        } catch (Exception e) {
            logger.error("Unexpected error during validation for jobId={}", entity != null ? entity.getJobId() : "<unknown>", e);
            if (entity != null && hasSetter(entity, "setFailureReason")) entity.setFailureReason(e.getMessage());
        }
        return entity;
    }

    // Helper to avoid compile-time errors if setter doesn't exist at runtime - naive check
    private boolean hasSetter(ExtractionJob entity, String setterName) {
        try {
            entity.getClass().getMethod(setterName, String.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
