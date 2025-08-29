package com.java_template.application.processor;
import com.java_template.application.entity.report.version_1.Report;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class StoreReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StoreReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public StoreReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Report.class)
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

    private boolean isValidEntity(Report entity) {
        return entity != null && entity.isValid();
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report entity = context.entity();

        try {
            // Ensure report has business id
            if (entity.getReportId() == null || entity.getReportId().isBlank()) {
                entity.setReportId(UUID.randomUUID().toString());
            }

            // Ensure generatedAt timestamp
            if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
                entity.setGeneratedAt(Instant.now().toString());
            }

            // Ensure status
            if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                entity.setStatus("GENERATED");
            }

            // Persist the Report entity (add new item)
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Report.ENTITY_NAME,
                Report.ENTITY_VERSION,
                entity
            );
            UUID persistedId = idFuture.get();
            logger.info("Persisted Report entity with technical id: {}", persistedId);

            // After persisting, attempt to update the originating ReportJob (if jobTechnicalId present)
            // Update job status/completedAt to reflect report creation.
            if (entity.getJobTechnicalId() != null && !entity.getJobTechnicalId().isBlank()) {
                try {
                    UUID jobUuid = UUID.fromString(entity.getJobTechnicalId());
                    CompletableFuture<DataPayload> jobFuture = entityService.getItem(jobUuid);
                    DataPayload jobPayload = jobFuture.get();
                    if (jobPayload != null && jobPayload.getData() != null) {
                        ReportJob job = objectMapper.treeToValue(jobPayload.getData(), ReportJob.class);
                        if (job != null) {
                            job.setStatus("COMPLETED");
                            // Use generatedAt from report for completedAt when available
                            job.setCompletedAt(entity.getGeneratedAt() != null ? entity.getGeneratedAt() : Instant.now().toString());
                            // Persist updated job (allowed because we are not updating the entity that triggered THIS processor if it wasn't the job)
                            CompletableFuture<UUID> updated = entityService.updateItem(jobUuid, job);
                            updated.get();
                            logger.info("Updated ReportJob ({}) status to COMPLETED", jobUuid);
                        }
                    } else {
                        logger.warn("ReportJob payload not found for id {}", jobUuid);
                    }
                } catch (Exception ex) {
                    logger.error("Failed to update ReportJob linked to report: {}", ex.getMessage(), ex);
                    // Do not fail the whole processing; report is persisted. Continue.
                }
            }

            // Optionally adjust the in-memory entity to include the technical id as reportId if business requires it.
            // Here we keep the business reportId (already set). If needed by consumers, we could set it to persistedId.toString().
            // For safety, set visualizationUrl to null if absent (no-op).
            return entity;
        } catch (Exception e) {
            logger.error("Failed to store Report entity: {}", e.getMessage(), e);
            // Mark entity as failed status for downstream handling
            try {
                entity.setStatus("FAILED");
                if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
                    entity.setGeneratedAt(Instant.now().toString());
                }
            } catch (Exception ignore) {}
            return entity;
        }
    }
}