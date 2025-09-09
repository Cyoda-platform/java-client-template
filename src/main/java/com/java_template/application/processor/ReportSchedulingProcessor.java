package com.java_template.application.processor;

import com.java_template.application.entity.report_entity.version_1.ReportEntity;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ReportSchedulingProcessor - Schedule weekly report generation
 * Transition: schedule_report (none → scheduled)
 */
@Component
public class ReportSchedulingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportSchedulingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReportSchedulingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportScheduling for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(ReportEntity.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<ReportEntity> entityWithMetadata) {
        return entityWithMetadata != null && entityWithMetadata.metadata() != null && 
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<ReportEntity> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<ReportEntity> context) {

        EntityWithMetadata<ReportEntity> entityWithMetadata = context.entityResponse();
        ReportEntity entity = entityWithMetadata.entity();

        logger.debug("Scheduling report generation");

        // Schedule the report
        scheduleReport(entity);

        logger.info("Report scheduled successfully: {}", entity.getReportId());

        return entityWithMetadata;
    }

    /**
     * Schedule weekly report generation
     */
    private void scheduleReport(ReportEntity entity) {
        // Calculate report period (last 7 days)
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(7);

        // Populate report entity
        entity.setReportId(UUID.randomUUID().toString());
        entity.setReportType("WEEKLY_PERFORMANCE");
        entity.setGenerationDate(LocalDateTime.now());

        // Set report period
        ReportEntity.ReportPeriod reportPeriod = new ReportEntity.ReportPeriod();
        reportPeriod.setStartDate(startDate);
        reportPeriod.setEndDate(endDate);
        entity.setReportPeriod(reportPeriod);

        // Set file format
        entity.setFileFormat("PDF");

        logger.debug("Report scheduled for period: {} to {}", startDate, endDate);
    }
}
