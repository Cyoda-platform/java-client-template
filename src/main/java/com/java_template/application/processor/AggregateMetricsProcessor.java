package com.java_template.application.processor;

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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class AggregateMetricsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AggregateMetricsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AggregateMetricsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AggregateMetrics for request: {}", request.getId());

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
            // For demo purposes build simple metrics object
            ObjectNode metrics = report.getMetrics() != null ? (ObjectNode) report.getMetrics() : report.objectMapper().createObjectNode();
            Map<String, Integer> perType = new HashMap<>();
            // In real implementation we would fetch activities by date and compute counts.
            perType.put("login", 50);
            perType.put("purchase", 10);
            metrics.put("totalActivities", 60);
            ObjectNode perTypeNode = report.objectMapper().createObjectNode();
            perType.forEach(perTypeNode::put);
            metrics.set("perType", perTypeNode);
            report.setMetrics(metrics);
            report.setGeneratedAt(Instant.now().toString());
            logger.info("Aggregated metrics for report date {}", report.getReportDate());
        } catch (Exception ex) {
            logger.error("Error aggregating metrics", ex);
            report.setFailureReason("aggregate error: " + ex.getMessage());
        }

        return report;
    }
}
