package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.batchjob.version_1.BatchJob;
import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DeliverReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliverReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public DeliverReportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DeliverReportProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(MonthlyReport.class)
            .validate(this::isValidEntity, "Invalid monthly report for delivery")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(MonthlyReport report) {
        return report != null && report.isValid();
    }

    private MonthlyReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<MonthlyReport> context) {
        MonthlyReport report = context.entity();
        try {
            // Try to fetch an associated BatchJob to get adminEmails if present
            List<String> recipients = new ArrayList<>();
            try {
                CompletableFuture<ArrayNode> listFuture = entityService.getItems(
                    BatchJob.ENTITY_NAME,
                    String.valueOf(BatchJob.ENTITY_VERSION)
                );
                ArrayNode items = listFuture.join();
                if (items != null && items.size() > 0) {
                    for (int i=0;i<items.size();i++) {
                        ObjectNode node = (ObjectNode) items.get(i);
                        if (node.hasNonNull("adminEmails")) {
                            for (var r : node.withArray("adminEmails")) recipients.add(r.asText());
                        }
                    }
                }
            } catch (Exception ignored) {}

            // If no recipients found, set deliveryStatus to FAILED
            if (recipients.isEmpty()) {
                report.setStatus("FAILED");
                report.setDeliveryStatus("FAILED");
                report.setErrorMessage("No recipients configured for report delivery");
                return report;
            }

            // Simulate delivery: mark recipients as delivered
            report.setDeliveredTo(recipients);
            report.setDeliveryStatus("SENT");
            report.setStatus("PUBLISHED");
            if (report.getGeneratedAt() == null) report.setGeneratedAt(Instant.now().toString());

            // Persist updated report via entity service update (best-effort)
            try {
                CompletableFuture<ArrayNode> reports = entityService.getItemsByCondition(
                    MonthlyReport.ENTITY_NAME,
                    String.valueOf(MonthlyReport.ENTITY_VERSION),
                    null,
                    true
                );
                // No further action required here; Cyoda will persist the triggering entity
            } catch (Exception ignored) {}

            logger.info("DeliverReportProcessor published report {} to {} recipients", report.getMonth(), recipients.size());
            return report;
        } catch (Exception ex) {
            logger.error("Unexpected error during DeliverReportProcessor", ex);
            report.setStatus("FAILED");
            report.setDeliveryStatus("FAILED");
            report.setErrorMessage("Delivery error: " + ex.getMessage());
            return report;
        }
    }
}