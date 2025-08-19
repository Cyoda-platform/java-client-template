package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.extractionjob.version_1.ExtractionJob;
import com.java_template.application.entity.report.version_1.Report;
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
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class NotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public NotificationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Notification for request: {}", request.getId());

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
        return entity != null && entity.getRecipients() != null && !entity.getRecipients().isEmpty();
    }

    private ExtractionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ExtractionJob> context) {
        ExtractionJob job = context.entity();
        try {
            // For prototype, fetch the last report generated from this job
            CompletableFuture<ObjectNode> reportFuture = entityService.getItem(Report.ENTITY_NAME, String.valueOf(Report.ENTITY_VERSION), UUID.fromString(job.getTechnicalId()));
            ObjectNode reportNode = reportFuture.get();
            if (reportNode == null) {
                logger.warn("No report found for jobId={} technicalId={}", job.getJobId(), job.getTechnicalId());
                return job;
            }

            // Simulate email sending - in production integrate with email provider
            logger.info("Sending report {} to recipients={} for jobId={}", reportNode.get("reportId").asText(), String.join(",", job.getRecipients()), job.getJobId());

            // On success set report status to SENT
            try {
                Report r = new Report();
                if (reportNode.has("reportId")) r.setReportId(reportNode.get("reportId").asText());
                if (reportNode.has("technicalId")) r.setTechnicalId(reportNode.get("technicalId").asText());
                r.setStatus("SENT");
                CompletableFuture<UUID> updated = entityService.updateItem(Report.ENTITY_NAME, String.valueOf(Report.ENTITY_VERSION), UUID.fromString(r.getTechnicalId()), r);
                updated.get();
            } catch (Exception e) {
                logger.warn("Failed to update report status after sending : {}", e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error during notification for jobId={}", job.getJobId(), e);
        }
        return job;
    }
}
