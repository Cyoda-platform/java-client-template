package com.java_template.application.processor;

import com.java_template.application.entity.product_performance_report.version_1.ProductPerformanceReport;
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
 * Sends email notification with report summary to sales team
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

        // Build email content
        String emailBody = buildEmailBody(report);
        String emailSubject = buildEmailSubject(report);

        // Simulate sending email
        sendEmail(report.getRecipientEmail(), emailSubject, emailBody);

        report.setUpdatedAt(LocalDateTime.now());
        logger.info("Email notification sent for report: {} to {}", report.getReportId(), report.getRecipientEmail());
        return entityWithMetadata;
    }

    private String buildEmailSubject(ProductPerformanceReport report) {
        return String.format("Product Performance Report - Week %s", report.getReportWeek());
    }

    private String buildEmailBody(ProductPerformanceReport report) {
        StringBuilder body = new StringBuilder();
        body.append("Dear Sales Team,\n\n");
        body.append("Please find below the weekly product performance summary:\n\n");
        body.append("=== PERFORMANCE SUMMARY ===\n");
        body.append("Report Week: ").append(report.getReportWeek()).append("\n");
        body.append("Total Revenue: $").append(String.format("%.2f", report.getTotalRevenue())).append("\n");
        body.append("Total Sales Volume: ").append(report.getTotalSalesVolume()).append(" units\n");
        body.append("Average Inventory Turnover: ").append(String.format("%.2f", report.getAverageInventoryTurnover())).append("\n\n");

        if (report.getTopSellingProducts() != null && !report.getTopSellingProducts().isEmpty()) {
            body.append("=== TOP SELLING PRODUCTS ===\n");
            report.getTopSellingProducts().forEach(p -> body.append("- ").append(p).append("\n"));
            body.append("\n");
        }

        if (report.getRestockingRecommendations() != null && !report.getRestockingRecommendations().isEmpty()) {
            body.append("=== URGENT RESTOCKING NEEDED ===\n");
            report.getRestockingRecommendations().stream()
                    .filter(r -> "high".equals(r.getUrgency()))
                    .forEach(r -> {
                        body.append("- ").append(r.getProductName()).append(": ");
                        body.append(r.getRecommendedQuantity()).append(" units\n");
                    });
            body.append("\n");
        }

        body.append("For detailed analysis, please access the full report in the system.\n\n");
        body.append("Best regards,\n");
        body.append("Product Performance Analysis System\n");

        return body.toString();
    }

    private void sendEmail(String recipient, String subject, String body) {
        // Simulate email sending - in production, this would use JavaMailSender
        logger.info("=== EMAIL NOTIFICATION ===");
        logger.info("To: {}", recipient);
        logger.info("Subject: {}", subject);
        logger.info("Body:\n{}", body);
        logger.info("=== EMAIL SENT SUCCESSFULLY ===");
    }
}

