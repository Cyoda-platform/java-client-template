package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
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
            InventoryReport report = null;

            // If AggregateMetricsProcessor persisted an intermediate report and attached its id to job.reportRef, fetch it
            if (job.getReportRef() != null && !job.getReportRef().trim().isEmpty()) {
                try {
                    CompletableFuture<com.fasterxml.jackson.databind.node.ObjectNode> fut = entityService.getItem(
                        InventoryReport.ENTITY_NAME,
                        String.valueOf(InventoryReport.ENTITY_VERSION),
                        java.util.UUID.fromString(job.getReportRef())
                    );
                    ObjectNode node = fut.get();
                    if (node != null) {
                        report = objectMapper.convertValue(node, InventoryReport.class);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to fetch intermediate report {}: {}", job.getReportRef(), e.getMessage());
                    report = null;
                }
            }

            // If no intermediate report found, try to build from job.metadata if present (legacy support)
            Map<String, Object> computed = null;
            try {
                Object meta = job.getMetadata();
                if (meta instanceof Map) {
                    //noinspection unchecked
                    computed = (Map<String, Object>) meta;
                }
            } catch (Exception ignored) { }

            if (report == null) {
                // Create new report skeleton
                report = new InventoryReport();
                report.setJobRef(job.getTechnicalId());
                report.setReportName(job.getJobName());
                report.setGeneratedAt(OffsetDateTime.now());
            }

            if ((computed == null || computed.get("metrics") == null) && (report.getMetricsSummary() == null || report.getMetricsSummary().isEmpty())) {
                // No metrics available -> EMPTY report
                report.setStatus("EMPTY");
                report.setSuggestion("No data available for the requested filters/metrics. Consider broadening filters or enabling price enrichment.");
                // Persist the report (create or update)
                persistOrUpdateReport(report);
                job.setReportRef(report.getTechnicalId());
                job.setStatus("COMPLETED");
                return job;
            }

            // Use metrics from computed or from existing report
            Map<String, Object> metrics = report.getMetricsSummary();
            if ((metrics == null || metrics.isEmpty()) && computed != null) {
                //noinspection unchecked
                metrics = (Map<String, Object>) computed.get("metrics");
            }
            java.util.List<Map<String, Object>> grouped = report.getGroupedSummaries();
            if ((grouped == null || grouped.isEmpty()) && computed != null) {
                //noinspection unchecked
                grouped = (java.util.List<Map<String, Object>>) computed.get("grouped");
            }

            // Build presentation payload
            ObjectNode payload = objectMapper.createObjectNode();
            payload.set("metricsSummary", objectMapper.valueToTree(metrics));
            payload.set("groupedSummaries", objectMapper.valueToTree(grouped));

            report.setMetricsSummary(metrics);
            report.setGroupedSummaries(grouped);
            report.setPresentationPayload(objectMapper.convertValue(payload, Map.class));
            report.setStatus("SUCCESS");
            report.setGeneratedAt(OffsetDateTime.now());
            report.setRetentionUntil(job.getRetentionUntil());

            // Persist or update report
            persistOrUpdateReport(report);

            job.setReportRef(report.getTechnicalId());
            job.setStatus("COMPLETED");
        } catch (Exception e) {
            logger.error("Error formatting report for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            try {
                InventoryReport errReport = new InventoryReport();
                errReport.setJobRef(job.getTechnicalId());
                errReport.setReportName(job.getJobName());
                errReport.setGeneratedAt(OffsetDateTime.now());
                errReport.setStatus("FAILED");
                errReport.setErrorMessage(e.getMessage());
                persistOrUpdateReport(errReport);
                job.setReportRef(errReport.getTechnicalId());
            } catch (Exception ex) {
                logger.error("Error persisting failed report: {}", ex.getMessage(), ex);
            }
            job.setStatus("FAILED");
        }
        return job;
    }

    private void persistOrUpdateReport(InventoryReport report) {
        try {
            if (report.getTechnicalId() != null && !report.getTechnicalId().trim().isEmpty()) {
                try {
                    CompletableFuture<java.util.UUID> fut = entityService.updateItem(
                        InventoryReport.ENTITY_NAME,
                        String.valueOf(InventoryReport.ENTITY_VERSION),
                        java.util.UUID.fromString(report.getTechnicalId()),
                        report
                    );
                    java.util.UUID id = fut.get();
                    if (id != null) report.setTechnicalId(id.toString());
                } catch (Exception e) {
                    logger.warn("Failed to update existing report {}, trying to add: {}", report.getTechnicalId(), e.getMessage());
                    // fallback to add
                    addReport(report);
                }
            } else {
                addReport(report);
            }
        } catch (Exception e) {
            logger.error("Failed to persist InventoryReport: {}", e.getMessage(), e);
            // fallback to generating a technical id
            if (report.getTechnicalId() == null) report.setTechnicalId(UUID.randomUUID().toString());
        }
    }

    private void addReport(InventoryReport report) throws Exception {
        CompletableFuture<java.util.UUID> fut = entityService.addItem(
            InventoryReport.ENTITY_NAME,
            String.valueOf(InventoryReport.ENTITY_VERSION),
            report
        );
        java.util.UUID id = fut.get();
        if (id != null) report.setTechnicalId(id.toString());
        else report.setTechnicalId(UUID.randomUUID().toString());
    }
}
