package com.java_template.application.processor;

import com.java_template.application.entity.analysisreport.version_1.AnalysisReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class ArchiveProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ArchiveProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AnalysisReport archive for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AnalysisReport.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AnalysisReport entity) {
        return entity != null && entity.isValid();
    }

    private AnalysisReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AnalysisReport> context) {
        AnalysisReport report = context.entity();
        try {
            logger.info("ArchiveProcessor invoked for reportTechnicalId={}", report.getTechnicalId());

            // Only archive if report is READY or CREATED (allow manual archival)
            String current = report.getStatus();
            if (current == null) current = "";
            if (!"READY".equalsIgnoreCase(current) && !"CREATED".equalsIgnoreCase(current)) {
                logger.info("ArchiveProcessor: report {} status is {} - skipping archival", report.getTechnicalId(), current);
                return report;
            }

            report.setStatus("ARCHIVED");
            report.setLast_updated_at(Instant.now().toString());

            // Persist update
            try {
                if (report.getTechnicalId() == null || report.getTechnicalId().isBlank()) {
                    CompletableFuture<java.util.UUID> added = entityService.addItem(
                        AnalysisReport.ENTITY_NAME,
                        String.valueOf(AnalysisReport.ENTITY_VERSION),
                        report
                    );
                    java.util.UUID id = added.get();
                    report.setTechnicalId(id.toString());
                } else {
                    CompletableFuture<java.util.UUID> updated = entityService.updateItem(
                        AnalysisReport.ENTITY_NAME,
                        String.valueOf(AnalysisReport.ENTITY_VERSION),
                        java.util.UUID.fromString(report.getTechnicalId()),
                        report
                    );
                    updated.get();
                }
            } catch (Exception e) {
                logger.error("Failed to persist archival of AnalysisReport {}: {}", report.getTechnicalId(), e.getMessage(), e);
                // revert status to previous to avoid accidental archive marking on failure
                report.setStatus(current);
                return report;
            }

            logger.info("ArchiveProcessor archived report {}", report.getTechnicalId());
            return report;
        } catch (Exception ex) {
            logger.error("Unexpected error in ArchiveProcessor for report {}: {}", report.getTechnicalId(), ex.getMessage(), ex);
            return report;
        }
    }
}
