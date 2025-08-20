package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class GenerateReportContentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenerateReportContentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public GenerateReportContentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing GenerateReportContent for request: {}", request.getId());

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
        return entity != null && entity.getReportDate() != null;
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report report = context.entity();

        try {
            report.setGeneratedAt(Instant.now().toString());
            // Try to fetch activity metrics via EntityService (Metrics entity or Activity scans)
            // For now, compute from existing metrics if present
            ObjectNode metrics = report.getMetrics() != null ? (ObjectNode) report.getMetrics() : report.objectMapper().createObjectNode();
            if (!metrics.has("totalActivities")) metrics.put("totalActivities", 0);
            if (!metrics.has("perType")) metrics.set("perType", report.objectMapper().createObjectNode());

            // Build summary text
            String summary = "Report for " + report.getReportDate() + ": total activities=" + metrics.get("totalActivities").asInt();
            report.setSummary(summary);

            // anomalies array - leave as provided or empty
            ArrayNode anomalies = report.getAnomalies() == null ? report.objectMapper().createArrayNode() : report.objectMapper().valueToTree(report.getAnomalies());
            report.setAnomalies(anomalies);

            logger.info("Generated report content for {}", report.getReportDate());
        } catch (Exception ex) {
            logger.error("Error generating report content", ex);
            report.setFailureReason("generate content error: " + ex.getMessage());
        }

        return report;
    }
}
