package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.extractionjob.version_1.ExtractionJob;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
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
            // Find latest report created from this job
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.createdFromJobId", "EQUALS", job.getJobId())
            );
            CompletableFuture<ArrayNode> reportsFuture = entityService.getItemsByCondition(Report.ENTITY_NAME, String.valueOf(Report.ENTITY_VERSION), condition, true);
            ArrayNode reports = reportsFuture.get();
            if (reports == null || reports.size() == 0) {
                logger.warn("No report found for jobId={} technicalId={}", job.getJobId(), job.getTechnicalId());
                return job;
            }

            // pick the most recently generated report by generatedAt
            ObjectNode chosen = null;
            for (int i = 0; i < reports.size(); i++) {
                ObjectNode node = (ObjectNode) reports.get(i);
                if (chosen == null) chosen = node;
                else {
                    String g1 = chosen.has("generatedAt") ? chosen.get("generatedAt").asText() : null;
                    String g2 = node.has("generatedAt") ? node.get("generatedAt").asText() : null;
                    if (g2 != null && (g1 == null || g2.compareTo(g1) > 0)) chosen = node;
                }
            }

            if (chosen == null) {
                logger.warn("No suitable report to send for jobId={}", job.getJobId());
                return job;
            }

            String reportId = chosen.has("reportId") ? chosen.get("reportId").asText() : "<unknown>";
            logger.info("Sending report {} to recipients={} for jobId={}", reportId, String.join(",", job.getRecipients()), job.getJobId());

            // Simulate email send by attempting to reach any attachment URL if it's an http(s) url to detect transient failures
            boolean simulatedSendSuccess = true;
            if (chosen.has("attachments") && chosen.get("attachments”).isArray()) {
                for (int i = 0; i < chosen.get("attachments").size(); i++) {
                    try {
                        ObjectNode att = (ObjectNode) chosen.get("attachments").get(i);
                        if (att.has("url") && att.get("url").asText().startsWith("http")) {
                            String url = att.get("url").asText();
                            try {
                                URL u = new URL(url);
                                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                                conn.setRequestMethod("HEAD");
                                conn.setConnectTimeout(3000);
                                conn.setReadTimeout(3000);
                                int status = conn.getResponseCode();
                                if (status >= 500) {
                                    simulatedSendSuccess = false;
                                    break;
                                }
                            } catch (Exception e) {
                                simulatedSendSuccess = false;
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            // Update report status based on simulated result
            Report r = new Report();
            if (chosen.has("technicalId")) r.setTechnicalId(chosen.get("technicalId").asText());
            if (chosen.has("reportId")) r.setReportId(chosen.get("reportId").asText());
            if (simulatedSendSuccess) {
                r.setStatus("SENT");
            } else {
                r.setStatus("FAILED");
            }
            // Persist report status update
            try {
                if (r.getTechnicalId() != null) {
                    entityService.updateItem(Report.ENTITY_NAME, String.valueOf(Report.ENTITY_VERSION), UUID.fromString(r.getTechnicalId()), r).get();
                    logger.info("Updated report {} status={} for jobId={}", r.getReportId(), r.getStatus(), job.getJobId());
                } else {
                    logger.warn("Report technicalId missing, cannot update status for reportId={} jobId={}", r.getReportId(), job.getJobId());
                }
            } catch (Exception e) {
                logger.warn("Failed to update report status after sending : {}", e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error during notification for jobId={}", job.getJobId(), e);
        }
        return job;
    }
}
