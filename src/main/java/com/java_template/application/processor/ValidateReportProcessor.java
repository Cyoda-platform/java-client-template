package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class ValidateReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ValidateReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AnalysisReport validation for request: {}", request.getId());

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
            logger.info("ValidateReportProcessor starting for reportTechnicalId={}", report.getTechnicalId());
            report.setStatus("VALIDATING");

            String metricsJson = report.getSummary_metrics();
            if (metricsJson == null || metricsJson.isBlank()) {
                report.setStatus("FAILED");
                logger.warn("Report {} missing summary_metrics", report.getTechnicalId());
                return report;
            }

            JsonNode metrics = objectMapper.readTree(metricsJson);
            // Required metrics checks
            if (!metrics.has("total_records") || !metrics.has("mean_price") || !metrics.has("median_price") || !metrics.has("distribution_by_neighbourhood") || !metrics.has("missing_value_counts")) {
                report.setStatus("FAILED");
                logger.warn("Report {} missing required metric fields", report.getTechnicalId());
                return report;
            }

            int totalRecords = metrics.get("total_records").asInt(-1);
            if (totalRecords <= 0 || report.getRecord_count() == null || report.getRecord_count() != totalRecords) {
                report.setStatus("FAILED");
                logger.warn("Report {} record counts mismatch or invalid: metrics total_records={} report.record_count={}", report.getTechnicalId(), totalRecords, report.getRecord_count());
                return report;
            }

            // All checks passed -> READY
            report.setStatus("READY");
            report.setLast_updated_at(Instant.now().toString());

            // Persist update for report via entityService update (allowed since this is a different entity from DataIngestJob)
            try {
                CompletableFuture<java.util.UUID> updated = entityService.updateItem(
                    AnalysisReport.ENTITY_NAME,
                    String.valueOf(AnalysisReport.ENTITY_VERSION),
                    java.util.UUID.fromString(report.getTechnicalId()),
                    report
                );
                updated.get();
            } catch (Exception e) {
                logger.error("Failed to update AnalysisReport {} after validation: {}", report.getTechnicalId(), e.getMessage(), e);
                report.setStatus("FAILED");
                return report;
            }

            logger.info("ValidateReportProcessor marked report {} as READY", report.getTechnicalId());
            return report;
        } catch (Exception ex) {
            logger.error("Unexpected error while validating report {}: {}", report.getTechnicalId(), ex.getMessage(), ex);
            report.setStatus("FAILED");
            return report;
        }
    }
}
