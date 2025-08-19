package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.entity.inventoryreportjob.version_1.InventoryReportJob;
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

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class ValidateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final Set<String> SUPPORTED_METRICS = new HashSet<>(Arrays.asList("totalCount", "avgPrice", "totalValue"));
    private static final Set<String> SUPPORTED_PRESENTATIONS = new HashSet<>(Arrays.asList("table", "chart"));

    public ValidateJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InventoryReportJob validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(InventoryReportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(InventoryReportJob entity) {
        return entity != null && entity.isValid();
    }

    private InventoryReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<InventoryReportJob> context) {
        InventoryReportJob job = context.entity();
        try {
            job.setStatus("VALIDATING");
            // basic checks
            if (job.getMetricsRequested() == null || job.getMetricsRequested().isEmpty()) {
                job.setStatus("VALIDATION_FAILED");
                return job;
            }
            for (String m : job.getMetricsRequested()) {
                if (!SUPPORTED_METRICS.contains(m)) {
                    logger.warn("Unsupported metric requested: {}", m);
                    job.setStatus("VALIDATION_FAILED");
                    return job;
                }
            }

            // filters is arbitrary JSON; ensure it's not malformed (it will be null or an object)
            JsonNode filters = job.getFilters();
            if (filters != null && !filters.isObject()) {
                logger.warn("Filters must be an object");
                job.setStatus("VALIDATION_FAILED");
                return job;
            }

            if (job.getPresentationType() != null && !SUPPORTED_PRESENTATIONS.contains(job.getPresentationType())) {
                logger.warn("Unsupported presentation type: {}", job.getPresentationType());
                job.setStatus("VALIDATION_FAILED");
                return job;
            }

            // schedule validation: if present, we accept object or null; here we just check createdAt
            if (job.getCreatedAt() == null) {
                job.setCreatedAt(OffsetDateTime.now());
            }

            job.setStatus("EXECUTING");
        } catch (Exception e) {
            logger.error("Error validating job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            job.setStatus("VALIDATION_FAILED");
        }
        return job;
    }
}
