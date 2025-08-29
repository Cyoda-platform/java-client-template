package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

@Component
public class LinkResourcesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LinkResourcesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public LinkResourcesProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Lightweight validation: ensure entity exists and has a status string.
     * We intentionally avoid calling entity.isValid() here because LinkResourcesProcessor
     * must be able to operate on reports that may be missing generatedAt/visualizationUrl
     * and other fields which will be added by this processor.
     */
    private boolean isValidEntity(Report entity) {
        return entity != null && entity.getStatus() != null && !entity.getStatus().isBlank();
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report entity = context.entity();

        if (entity == null) return null;

        try {
            // If the report has completed rendering, link resources and mark as AVAILABLE
            String currentStatus = entity.getStatus();
            if (currentStatus != null && "COMPLETED".equalsIgnoreCase(currentStatus.trim())) {

                // Ensure generatedAt is set
                if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
                    entity.setGeneratedAt(Instant.now().toString());
                }

                // If visualizationUrl missing but reportId present, create a default artifact path
                if ((entity.getVisualizationUrl() == null || entity.getVisualizationUrl().isBlank())
                        && entity.getReportId() != null && !entity.getReportId().isBlank()) {
                    String vizPath = "/artifacts/" + entity.getReportId() + "/chart.png";
                    entity.setVisualizationUrl(vizPath);
                }

                // Normalize bookingsSample persistedAt fields
                List<Report.BookingSummary> sample = entity.getBookingsSample();
                if (sample != null) {
                    for (Report.BookingSummary b : sample) {
                        if (b != null) {
                            if (b.getPersistedAt() == null || b.getPersistedAt().isBlank()) {
                                b.setPersistedAt(entity.getGeneratedAt());
                            }
                        }
                    }
                }

                // Transition status to AVAILABLE
                entity.setStatus("AVAILABLE");
                logger.info("Linked resources for report {} and set status to AVAILABLE", entity.getReportId());
            } else {
                logger.debug("Report status is not COMPLETED (current: {}), no resource linking performed", currentStatus);
            }
        } catch (Exception ex) {
            logger.error("Error while linking resources for report {}: {}", entity.getReportId(), ex.getMessage(), ex);
            // Mark report as FAILED if linking fails
            try {
                entity.setStatus("FAILED");
            } catch (Exception ignore) {
                logger.warn("Unable to set report status to FAILED for report {}", entity != null ? entity.getReportId() : "unknown");
            }
        }

        return entity;
    }
}