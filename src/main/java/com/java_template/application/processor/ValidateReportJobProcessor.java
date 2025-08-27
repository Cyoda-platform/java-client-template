package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
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

import java.util.Map;
import java.util.Set;

@Component
public class ValidateReportJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateReportJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateReportJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ReportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportJob entity) {
        return entity != null && entity.isValid();
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob entity = context.entity();

        // Basic defensive check
        if (entity == null) {
            logger.warn("ReportJob entity is null in processing context");
            return null;
        }

        boolean valid = true;

        // Re-check core validity (redundant with isValidEntity but keeps explicit logs)
        if (!entity.isValid()) {
            logger.warn("ReportJob failed basic validation (isValid returned false). Title: {}, RequestedBy: {}", entity.getTitle(), entity.getRequestedBy());
            valid = false;
        }

        // Validate export formats: must be non-empty strings and limited to known formats (CSV, PDF) per requirements.
        Set<String> allowedFormats = Set.of("CSV", "PDF");
        if (entity.getExportFormats() == null || entity.getExportFormats().isEmpty()) {
            logger.warn("ReportJob exportFormats is null or empty");
            valid = false;
        } else {
            for (String fmt : entity.getExportFormats()) {
                if (fmt == null || fmt.isBlank()) {
                    logger.warn("ReportJob contains blank export format");
                    valid = false;
                    break;
                }
                // allow lowercase by normalizing
                String normalized = fmt.trim().toUpperCase();
                if (!allowedFormats.contains(normalized)) {
                    logger.warn("ReportJob contains unsupported export format: {}", fmt);
                    valid = false;
                    break;
                }
            }
        }

        // Validate filters: if provided, keys and values should not be blank
        Map<String, String> filters = entity.getFilters();
        if (filters != null) {
            for (Map.Entry<String, String> e : filters.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                if (k == null || k.isBlank()) {
                    logger.warn("ReportJob contains blank filter key");
                    valid = false;
                    break;
                }
                if (v == null || v.isBlank()) {
                    logger.warn("ReportJob contains blank filter value for key {}", k);
                    valid = false;
                    break;
                }
            }
        }

        // Validate notify if present (must not be blank)
        if (entity.getNotify() != null && entity.getNotify().isBlank()) {
            logger.warn("ReportJob notify field is blank");
            valid = false;
        }

        // Set appropriate status based on validation outcome.
        if (!valid) {
            logger.info("Validation failed for ReportJob titled '{}'. Marking status as FAILED", entity.getTitle());
            entity.setStatus("FAILED");
        } else {
            logger.info("Validation passed for ReportJob titled '{}'. Marking status as IN_PROGRESS", entity.getTitle());
            entity.setStatus("IN_PROGRESS");
        }

        // Return the mutated entity. Cyoda will persist this entity state automatically.
        return entity;
    }
}