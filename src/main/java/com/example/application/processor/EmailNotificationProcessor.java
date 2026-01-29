package com.example.application.processor;

import com.example.application.entity.product_performance_report.version_1.ProductPerformanceReport;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * EmailNotificationProcessor
 * Sends email notification with report to sales team
 */
@Component
public class EmailNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailNotificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing email notification for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(ProductPerformanceReport.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEmailNotification)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(
            EntityWithMetadata<ProductPerformanceReport> entityWithMetadata) {
        ProductPerformanceReport entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<ProductPerformanceReport> processEmailNotification(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<ProductPerformanceReport> context) {

        EntityWithMetadata<ProductPerformanceReport> entityWithMetadata = context.entityResponse();
        ProductPerformanceReport report = entityWithMetadata.entity();

        logger.debug("Sending email notification for report: {}", report.getReportId());

        try {
            // Prepare email
            String recipientEmail = report.getRecipientEmail();
            String subject = String.format("Weekly Product Performance Report - %s", report.getReportWeek());
            String emailBody = buildEmailBody(report);

            // Simulate sending email
            sendEmail(recipientEmail, subject, emailBody, report.getReportContent());

            logger.info("Email notification sent successfully to: {}", recipientEmail);
        } catch (Exception e) {
            logger.error("Failed to send email notification for report: {}", report.getReportId(), e);
            throw new RuntimeException("Email notification failed", e);
        }

        report.setUpdatedAt(LocalDateTime.now());
        return entityWithMetadata;
    }

    private String buildEmailBody(ProductPerformanceReport report) {
        StringBuilder body = new StringBuilder();

        body.append("Dear Sales Team,\n\n");
        body.append("Please find attached the weekly product performance report for ").append(report.getReportWeek()).append(".\n\n");
        body.append("SUMMARY:\n");
        body.append(report.getReportSummary()).append("\n\n");

        body.append("KEY METRICS:\n");
        body.append("• Total Sales Volume: ").append(report.getTotalSalesVolume()).append(" units\n");
        body.append("• Total Revenue: $").append(String.format("%.2f", report.getTotalRevenue())).append("\n");
        body.append("• Average Inventory Turnover: ").append(String.format("%.2f", report.getAverageInventoryTurnover())).append("\n\n");

        body.append("Please review the attached detailed report for comprehensive analysis and recommendations.\n\n");
        body.append("Best regards,\n");
        body.append("Product Performance Analysis System\n");

        return body.toString();
    }

    private void sendEmail(String recipientEmail, String subject, String body, String attachmentContent) {
        // Simulate email sending
        logger.info("Simulating email send to: {}", recipientEmail);
        logger.info("Subject: {}", subject);
        logger.debug("Body preview: {}", body.substring(0, Math.min(100, body.length())));
        logger.debug("Attachment size: {} bytes", attachmentContent != null ? attachmentContent.length() : 0);

        // In a real implementation, this would use JavaMailSender or similar
        // For now, we just log the operation
    }
}

