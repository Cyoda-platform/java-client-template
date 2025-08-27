package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ExportReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExportReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ExportReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Report.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
            // If report already has storageLocation set, we consider it exported; leave as-is.
            if (entity.getStorageLocation() != null && !entity.getStorageLocation().isBlank()) {
                logger.info("Report {} already has storageLocation {}", entity.getId(), entity.getStorageLocation());
                // Ensure summary mentions export
                if (entity.getSummary() == null || entity.getSummary().isBlank()) {
                    entity.setSummary("Report already exported to " + entity.getStorageLocation());
                }
                return entity;
            }

            // Default export format
            String exportFormat = "csv";
            // Try to fetch the originating ReportJob to determine exportFormats and notify preferences
            String jobRef = entity.getJobReference();
            ReportJob job = null;
            if (jobRef != null && !jobRef.isBlank()) {
                try {
                    CompletableFuture<ObjectNode> jobFuture = entityService.getItem(
                        ReportJob.ENTITY_NAME,
                        String.valueOf(ReportJob.ENTITY_VERSION),
                        UUID.fromString(jobRef)
                    );
                    ObjectNode jobNode = jobFuture.join();
                    if (jobNode != null && !jobNode.isEmpty()) {
                        job = objectMapper.treeToValue(jobNode, ReportJob.class);
                        List<String> formats = job.getExportFormats();
                        if (formats != null && !formats.isEmpty()) {
                            // prefer first format, normalize to lowercase
                            exportFormat = formats.get(0).toLowerCase();
                            if (exportFormat.equals("pdf")) {
                                exportFormat = "pdf";
                            } else {
                                // fallback to csv for any unsupported/unknown format
                                exportFormat = "csv";
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to fetch ReportJob {}: {}", jobRef, ex.getMessage());
                }
            }

            // Compose a storage location for exported artifact (logical URI)
            String ext = exportFormat.equals("pdf") ? "pdf" : "csv";
            String storageLocation = String.format("s3://reports/%s/report.%s", entity.getId(), ext);
            entity.setStorageLocation(storageLocation);

            // Provide a human-readable summary if missing
            if (entity.getSummary() == null || entity.getSummary().isBlank()) {
                String summary = String.format("Report exported as %s to %s", ext.toUpperCase(), storageLocation);
                entity.setSummary(summary);
            }

            logger.info("Report {} prepared for export at {}", entity.getId(), storageLocation);

            // Update the originating ReportJob status (if available) to reflect exported state.
            // NOTE: We are allowed to update other entities; do not call update on Report itself.
            if (job != null && jobRef != null && !jobRef.isBlank()) {
                try {
                    // Do not overwrite existing meaningful statuses; set to EXPORTED if not already referencing export.
                    String jobStatus = job.getStatus();
                    if (jobStatus == null || jobStatus.isBlank() || !jobStatus.equalsIgnoreCase("EXPORTED")) {
                        job.setStatus("EXPORTED");
                        CompletableFuture<UUID> updated = entityService.updateItem(
                            ReportJob.ENTITY_NAME,
                            String.valueOf(ReportJob.ENTITY_VERSION),
                            UUID.fromString(jobRef),
                            job
                        );
                        updated.join();
                        logger.info("Updated ReportJob {} status to EXPORTED", jobRef);
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to update ReportJob {}: {}", jobRef, ex.getMessage());
                    // don't fail the export; just record summary
                    entity.setSummary(entity.getSummary() + " (ReportJob update failed)");
                }
            }

        } catch (Exception ex) {
            logger.error("Error during ExportReportProcessor for report {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
            // mark summary to indicate failure; allow workflow to persist this state
            if (entity != null) {
                String prev = entity.getSummary() == null ? "" : entity.getSummary() + " ";
                entity.setSummary(prev + "Export failed: " + ex.getMessage());
            }
        }

        return entity;
    }
}