package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.report.version_1.Report;
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
public class PublishReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PublishReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PublishReportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Publishing Report for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Report.class)
            .validate(this::isValidEntity, "Invalid report for publishing")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Report report) {
        return report != null && report.getReportId() != null;
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report report = context.entity();
        try {
            // Idempotency: if already sent, no-op
            if ("SENT".equalsIgnoreCase(report.getDeliveryStatus())) {
                logger.info("Report {} already sent, skipping", report.getReportId());
                return report;
            }

            // Simulate send: if recipient email present, mark SENT, else mark FAILED
            if (report.getRecipientEmail() != null && !report.getRecipientEmail().isEmpty()) {
                report.setDeliveryStatus("SENT");
                report.setDeliveryAttempts(report.getDeliveryAttempts() == null ? 1 : report.getDeliveryAttempts() + 1);
                report.setLastDeliveryResponse(MapBuilder.create("status", "200", "messageId", "msg-" + Instant.now().toEpochMilli()));

            } else {
                report.setDeliveryStatus("FAILED");
                report.setDeliveryAttempts(report.getDeliveryAttempts() == null ? 1 : report.getDeliveryAttempts() + 1);
                report.setLastDeliveryResponse(MapBuilder.create("status", "400", "error", "no-recipient"));
            }

            // persist report update
            CompletableFuture<java.util.UUID> updated = entityService.updateItem(Report.ENTITY_NAME, String.valueOf(Report.ENTITY_VERSION), java.util.UUID.fromString(report.getTechnicalId()), report);
            updated.get();

        } catch (Exception ex) {
            logger.error("Error publishing report {}: {}", report == null ? "<null>" : report.getReportId(), ex.getMessage(), ex);
            if (report != null) {
                report.setDeliveryStatus("FAILED");
            }
        }
        return report;
    }

    // small helper for building maps without external dependencies
    private static class MapBuilder {
        static java.util.Map<String, Object> create(Object... kv) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            for (int i = 0; i + 1 < kv.length; i += 2) {
                m.put(String.valueOf(kv[i]), kv[i + 1]);
            }
            return m;
        }
    }
}
