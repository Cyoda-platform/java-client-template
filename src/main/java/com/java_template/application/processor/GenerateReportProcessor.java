package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class GenerateReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenerateReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public GenerateReportProcessor(SerializerFactory serializerFactory,
                                   EntityService entityService,
                                   ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ReportJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
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

        // Business logic:
        // - Assemble a simple report artifact (summary + requested metrics references)
        // - Persist artifact location by updating entity.reportLocation
        // - Set generatedAt timestamp
        // - Update status to indicate report was generated (REPORTING -> NOTIFYING in next step of workflow)

        try {
            // Build a minimal report payload (metadata) using available entity properties
            Map<String, Object> reportMeta = new HashMap<>();
            reportMeta.put("jobId", entity.getJobId());
            reportMeta.put("dataSourceUrl", entity.getDataSourceUrl());
            reportMeta.put("requestedMetrics", entity.getRequestedMetrics());
            reportMeta.put("triggerType", entity.getTriggerType());
            reportMeta.put("statusBeforeGeneration", entity.getStatus());

            String generatedAt = Instant.now().toString();
            reportMeta.put("generatedAt", generatedAt);

            // Serialize metadata to JSON as a representation of the report
            String reportContent = objectMapper.writeValueAsString(reportMeta);

            // Determine a logical report storage location (this is a logical path; actual persistence handled by infra)
            String reportLocation = "internal-reports/" + (entity.getJobId() != null ? entity.getJobId() : "report") + ".json";

            // Log the generated report (in a real system this would be uploaded to storage)
            logger.info("Generated report for jobId={} at location={} content={}", entity.getJobId(), reportLocation, reportContent);

            // Update the entity state. Do NOT call entityService.updateItem on the same triggering entity.
            entity.setReportLocation(reportLocation);
            entity.setGeneratedAt(generatedAt);

            // Move status to REPORTING to reflect generation completed; downstream processors/criteria will continue workflow
            entity.setStatus("REPORTING");

            return entity;
        } catch (Exception ex) {
            logger.error("Failed to generate report for jobId={}: {}", entity != null ? entity.getJobId() : "unknown", ex.getMessage(), ex);
            // On failure mark entity as FAILED and set generatedAt if possible
            if (entity != null) {
                entity.setStatus("FAILED");
                entity.setGeneratedAt(Instant.now().toString());
            }
            return entity;
        }
    }
}