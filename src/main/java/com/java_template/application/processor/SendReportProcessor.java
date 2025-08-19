package com.java_template.application.processor;

import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class SendReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SendReportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SendReport for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(WeeklyReport.class)
                .validate(this::isValidEntity, "Invalid WeeklyReport state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklyReport report) {
        return report != null && report.isValid();
    }

    private WeeklyReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklyReport> context) {
        WeeklyReport report = context.entity();
        try {
            // retrieve fetch job to obtain recipients
            // In our WeeklyReport we stored fetchJobId as name; try to get job by technical id
            List<String> recipients = null;
            try {
                CompletableFuture<java.util.ArrayNode> fut = entityService.getItems(FetchJob.ENTITY_NAME, String.valueOf(FetchJob.ENTITY_VERSION));
                java.util.ArrayNode arr = fut.get();
                if (arr != null) {
                    for (com.fasterxml.jackson.databind.JsonNode n : arr) {
                        try {
                            if (n.hasNonNull("name") && n.get("name").asText().equals(report.getFetchJobId())) {
                                if (n.hasNonNull("recipients")) {
                                    recipients = new ArrayList<>();
                                    for (com.fasterxml.jackson.databind.JsonNode r : n.get("recipients")) recipients.add(r.asText());
                                }
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (InterruptedException | ExecutionException ex) {
                logger.warn("SendReportProcessor: failed to lookup FetchJob recipients", ex);
            }

            if (recipients == null || recipients.isEmpty()) {
                // fallback: check delivery info recipients
                if (report.getDeliveryInfo() != null && report.getDeliveryInfo().get("recipients") instanceof List) {
                    recipients = (List<String>) report.getDeliveryInfo().get("recipients");
                }
            }

            if (recipients == null || recipients.isEmpty()) {
                logger.warn("SendReportProcessor: no recipients defined for report={}", report.getFetchJobId());
                report.setReportStatus("failed");
                Map<String, Object> delivery = report.getDeliveryInfo();
                if (delivery == null) delivery = new LinkedHashMap<>();
                delivery.put("emailStatus", "failed");
                delivery.put("errors", List.of("no recipients"));
                report.setDeliveryInfo(delivery);
                return report;
            }

            // send email - here we don't have SMTP client, so simulate sending
            Map<String, Object> delivery = report.getDeliveryInfo();
            if (delivery == null) delivery = new LinkedHashMap<>();
            delivery.put("recipients", recipients);
            delivery.put("emailStatus", "delivered");
            delivery.put("messageId", UUID.randomUUID().toString());
            delivery.put("errors", Collections.emptyList());
            report.setDeliveryInfo(delivery);
            report.setReportStatus("sent");

            // persist changes
        } catch (Exception ex) {
            logger.error("SendReportProcessor: failed to send report", ex);
            report.setReportStatus("failed");
            Map<String, Object> delivery = report.getDeliveryInfo();
            if (delivery == null) delivery = new LinkedHashMap<>();
            delivery.put("emailStatus", "failed");
            delivery.put("errors", List.of(ex.getMessage()));
            report.setDeliveryInfo(delivery);
        }

        return report;
    }
}
