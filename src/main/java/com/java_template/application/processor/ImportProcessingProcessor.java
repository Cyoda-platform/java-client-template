package com.java_template.application.processor;

import com.java_template.application.entity.importjob.version_1.ImportJob;
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

import java.util.List;
import java.util.Map;

@Component
public class ImportProcessingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ImportProcessingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ImportProcessingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob records for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid import job for processing")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob job) {
        return job != null && job.getType() != null && (job.getFileUrl() != null || job.getResultSummary() != null);
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        try {
            // Simulate parsing payload/file and emitting per-record events
            Object payload = job.getResultSummary();
            // For prototype: record that processing started and increment counters
            Map<String, Object> summary = job.getResultSummary() instanceof Map ? (Map) job.getResultSummary() : new java.util.HashMap<>();
            summary.putIfAbsent("processed", 0);
            summary.put("processed", ((Integer) summary.get("processed")) + 1);
            job.setResultSummary(summary);
            job.setStatus("Processing");
            logger.info("ImportJob {} processing simulated; updated summary: {}", job.getId(), summary);
        } catch (Exception e) {
            logger.error("Error during import processing: {}", e.getMessage(), e);
            job.setStatus("Failed");
        }
        return job;
    }
}
