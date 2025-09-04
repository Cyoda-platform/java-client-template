package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class ReportEmailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportEmailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public ReportEmailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report email sending for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Report.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Report entity) {
        return entity != null && entity.isValid();
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report report = context.entity();

        try {
            String emailSubject = "Weekly Book Analytics Report - " + report.getReportId();
            String emailBody = composeEmailBody(report);
            List<String> recipients = Arrays.asList(report.getEmailRecipients().split(","));

            // Simulate email sending (in real implementation, this would use an email service)
            sendEmail(recipients, emailSubject, emailBody);

            report.setEmailSentAt(LocalDateTime.now());
            logger.info("Email sent successfully for report: {}", report.getReportId());

        } catch (Exception e) {
            logger.error("Failed to send email for report: {} - {}", report.getReportId(), e.getMessage(), e);
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }

        return report;
    }

    private String composeEmailBody(Report report) {
        StringBuilder body = new StringBuilder();
        body.append("Dear Analytics Team,\n\n");
        body.append("Please find the weekly book analytics report below:\n\n");
        body.append(report.getReportSummary()).append("\n\n");

        // Add popular titles section
        try {
            if (report.getPopularTitles() != null && !report.getPopularTitles().isEmpty()) {
                List<Map<String, Object>> popularTitles = objectMapper.readValue(
                    report.getPopularTitles(), new TypeReference<List<Map<String, Object>>>() {});
                
                body.append("Popular Titles (Top 10):\n");
                for (Map<String, Object> title : popularTitles) {
                    body.append("- ").append(title.get("title"))
                        .append(" (Score: ").append(title.get("score"))
                        .append(", Pages: ").append(title.get("pageCount")).append(")\n");
                }
                body.append("\n");
            }
        } catch (Exception e) {
            logger.warn("Failed to parse popular titles JSON: {}", e.getMessage());
            body.append("Popular Titles: Data parsing error\n\n");
        }

        // Add publication insights
        try {
            if (report.getPublicationDateInsights() != null && !report.getPublicationDateInsights().isEmpty()) {
                Map<String, Object> insights = objectMapper.readValue(
                    report.getPublicationDateInsights(), new TypeReference<Map<String, Object>>() {});
                
                body.append("Publication Date Insights:\n");
                body.append("- Years spanned: ").append(insights.get("totalYearsSpanned")).append("\n");
                body.append("- Most productive year: ").append(insights.get("mostProductiveYear"))
                    .append(" (").append(insights.get("booksInMostProductiveYear")).append(" books)\n");
                body.append("- Average books per year: ")
                    .append(String.format("%.2f", ((Number) insights.get("averageBooksPerYear")).doubleValue()))
                    .append("\n\n");
            }
        } catch (Exception e) {
            logger.warn("Failed to parse publication insights JSON: {}", e.getMessage());
            body.append("Publication Date Insights: Data parsing error\n\n");
        }

        body.append("Best regards,\nBook Analytics System");
        return body.toString();
    }

    private void sendEmail(List<String> recipients, String subject, String body) {
        // Simulate email sending - in a real implementation, this would integrate with an email service
        // such as SendGrid, AWS SES, or SMTP server
        
        logger.info("=== EMAIL SIMULATION ===");
        logger.info("To: {}", String.join(", ", recipients));
        logger.info("Subject: {}", subject);
        logger.info("Body:\n{}", body);
        logger.info("=== END EMAIL SIMULATION ===");
        
        // Simulate potential email service failures for testing
        if (Math.random() < 0.05) { // 5% chance of failure
            throw new RuntimeException("Simulated email service failure");
        }
        
        // Simulate email sending delay
        try {
            Thread.sleep(100); // 100ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Email sending interrupted", e);
        }
        
        logger.info("Email successfully sent to {} recipients", recipients.size());
    }
}
