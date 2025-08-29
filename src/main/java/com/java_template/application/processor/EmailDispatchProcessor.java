package com.java_template.application.processor;

import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class EmailDispatchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailDispatchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public EmailDispatchProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklyReport for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(WeeklyReport.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklyReport entity) {
        return entity != null && entity.isValid();
    }

    private WeeklyReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklyReport> context) {
        WeeklyReport entity = context.entity();

        // Business logic for dispatching weekly report via email.
        // 1. Ensure there is an attachment to send. If missing, mark FAILED and annotate summary.
        // 2. Compose an email payload and attempt to send via HTTP email service.
        // 3. On success set status = "DISPATCHED"; on failure set status = "FAILED" and update summary.
        // 4. Update generatedAt if needed to current timestamp of dispatch.

        String attachment = entity.getAttachmentUrl();
        String summary = entity.getSummary();
        if (summary == null) summary = "";

        if (attachment == null || attachment.isBlank()) {
            logger.warn("WeeklyReport {} has no attachmentUrl. Marking as FAILED.", entity.getReportId());
            entity.setStatus("FAILED");
            String appended = "Email dispatch failed: missing attachmentUrl.";
            entity.setSummary(mergeSummary(summary, appended));
            // Do not attempt to call external service if no attachment.
            return entity;
        }

        // Compose email content
        String subject = String.format("Weekly Report: %s (week starting %s)", safe(entity.getReportId()), safe(entity.getWeekStart()));
        String body = String.format("Weekly report generated at %s.\n\nSummary:\n%s\n\nAttachment: %s",
                safe(entity.getGeneratedAt()), safe(entity.getSummary()), attachment);

        // Default recipient per requirements
        String toEmail = "victoria.sagdieva@cyoda.com";

        // Prepare JSON payload for an external email service
        Map<String, Object> emailPayload = new HashMap<>();
        emailPayload.put("to", toEmail);
        emailPayload.put("subject", subject);
        emailPayload.put("body", body);
        emailPayload.put("attachmentUrl", attachment);

        try {
            String payload = objectMapper.writeValueAsString(emailPayload);

            // Use HttpClient to call external mail service.
            // The endpoint below is a placeholder; in a real deployment this should be replaced with an actual mail API.
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://example.com/api/sendEmail"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                logger.info("Email dispatched successfully for report {}. responseCode={}", entity.getReportId(), statusCode);
                entity.setStatus("DISPATCHED");
                entity.setGeneratedAt(Instant.now().toString());
                String appended = "Email dispatched to " + toEmail + ".";
                entity.setSummary(mergeSummary(summary, appended));
            } else {
                logger.error("Failed to dispatch email for report {}. responseCode={}, body={}", entity.getReportId(), statusCode, response.body());
                entity.setStatus("FAILED");
                String appended = "Email dispatch failed with status " + statusCode + ".";
                entity.setSummary(mergeSummary(summary, appended));
            }
        } catch (Exception e) {
            logger.error("Exception while dispatching email for report {}: {}", entity.getReportId(), e.getMessage(), e);
            entity.setStatus("FAILED");
            String appended = "Email dispatch failed: " + e.getMessage();
            entity.setSummary(mergeSummary(summary, appended));
        }

        return entity;
    }

    private String mergeSummary(String existing, String appended) {
        if (existing == null || existing.isBlank()) return appended;
        return existing + "\n\n" + appended;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}