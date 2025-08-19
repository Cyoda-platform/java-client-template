package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.inventoryreport.version_1.InventoryReport;
import com.java_template.application.entity.inventoryreportjob.version_1.InventoryReportJob;
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

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class FormatReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FormatReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FormatReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FormatReport for request: {}", request.getId());

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
            Object meta = job.getMetadata();
            Map<String, Object> computed = null;
            if (meta instanceof Map) {
                //noinspection unchecked
                computed = (Map<String, Object>) meta;
            }

            InventoryReport report = new InventoryReport();
            report.setJobRef(job.getTechnicalId());
            report.setReportName(job.getJobName());
            report.setGeneratedAt(OffsetDateTime.now());

            if (computed == null || computed.get("metrics") == null) {
                report.setStatus("EMPTY");
                report.setSuggestion("No data available for the requested filters/metrics. Consider broadening filters or enabling price enrichment.");
                // persist empty report
                persistReport(report);
                job.setReportRef(report.getTechnicalId());
                job.setStatus("COMPLETED");
                return job;
            }

            // Build presentation payload
            ObjectNode payload = objectMapper.createObjectNode();
            payload.set("metricsSummary", objectMapper.valueToTree(computed.get("metrics")));
            payload.set("groupedSummaries", objectMapper.valueToTree(computed.get("grouped")));
            report.setMetricsSummary(objectMapper.convertValue(computed.get("metrics"), Map.class));
            report.setGroupedSummaries(objectMapper.convertValue(computed.get("grouped"), java.util.List.class));
            report.setPresentationPayload(objectMapper.convertValue(payload, Map.class));
            report.setStatus("SUCCESS");
            report.setRetentionUntil(job.getRetentionUntil());

            // persist report
            persistReport(report);
            job.setReportRef(report.getTechnicalId());
            job.setStatus("COMPLETED");
        } catch (Exception e) {
            logger.error("Error formatting report for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            try {
                InventoryReport report = new InventoryReport();
                report.setJobRef(job.getTechnicalId());
                report.setGeneratedAt(OffsetDateTime.now());
                report.setStatus("FAILED");
                report.setErrorMessage(e.getMessage());
                persistReport(report);
            } catch (Exception ex) {
                logger.error("Error persisting failed report: {}", ex.getMessage(), ex);
            }
            job.setStatus("FAILED");
        }
        return job;
    }

    private void persistReport(InventoryReport report) {
        try {
            CompletableFuture<UUID> fut = entityService.addItem(
                InventoryReport.ENTITY_NAME,
                String.valueOf(InventoryReport.ENTITY_VERSION),
                report
            );
            UUID id = fut.get();
            if (id != null) {
                report.setTechnicalId(id.toString());
            } else {
                // generate fallback
                report.setTechnicalId(UUID.randomUUID().toString());
            }
        } catch (Exception e) {
            logger.error("Failed to persist InventoryReport: {}", e.getMessage(), e);
            // fallback to setting technicalId
            report.setTechnicalId(UUID.randomUUID().toString());
        }
    }
}
