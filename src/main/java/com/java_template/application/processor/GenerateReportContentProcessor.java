package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class GenerateReportContentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenerateReportContentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public GenerateReportContentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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
        return entity != null;
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report report = context.entity();

        try {
            // Build a simple textual summary and anomalies list from metrics
            ObjectNode metrics = report.getMetrics() != null ? (ObjectNode) report.getMetrics() : report.objectMapper().createObjectNode();
            String summary = "Daily activity summary for " + report.getReportDate();
            report.setSummary(summary);

            ArrayNode anomalies = report.objectMapper().createArrayNode();
            // In real implementation we'd query anomalies; here we add a sample if purchase > 5
            if (metrics.has("perType") && metrics.get("perType").has("purchase") && metrics.get("perType").get("purchase").asInt() > 5) {
                ObjectNode a = report.objectMapper().createObjectNode();
                a.put("reason", "higher than expected purchases");
                anomalies.add(a);
            }
            report.setAnomalies(anomalies);
            report.setGeneratedAt(Instant.now().toString());
            logger.info("Generated report content for {}", report.getReportDate());
        } catch (Exception ex) {
            logger.error("Error generating report content", ex);
            report.setFailureReason("generate content error: " + ex.getMessage());
        }

        return report;
    }
}
