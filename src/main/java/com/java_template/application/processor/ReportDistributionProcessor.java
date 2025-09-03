package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.emailnotification.version_1.EmailNotification;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class ReportDistributionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportDistributionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public ReportDistributionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report distribution for request: {}", request.getId());

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
        Report entity = context.entity();

        logger.info("Distributing report: {}", entity.getReportName());

        // Create EmailNotification entity
        EmailNotification emailNotification = createEmailNotification(entity);

        try {
            // Save the email notification entity (this will trigger the email workflow)
            entityService.save(emailNotification);
            logger.info("Email notification created for report: {}", entity.getReportName());
        } catch (Exception e) {
            logger.error("Failed to create email notification for report {}: {}", 
                        entity.getReportName(), e.getMessage());
            throw new RuntimeException("Failed to create email notification", e);
        }

        // Update report distribution timestamp (this would be done by the framework)
        logger.info("Report distribution initiated: {}", entity.getReportName());
        return entity;
    }

    private EmailNotification createEmailNotification(Report report) {
        EmailNotification notification = new EmailNotification();
        
        // Set report reference (in real implementation, this would be the technical ID)
        notification.setReportId(report.getId());
        
        // Set recipient email
        notification.setRecipientEmail("victoria.sagdieva@cyoda.com");
        
        // Set email subject
        String periodStr = report.getReportPeriodStart().format(DateTimeFormatter.ofPattern("MMM d")) + 
                          "-" + report.getReportPeriodEnd().format(DateTimeFormatter.ofPattern("d, yyyy"));
        notification.setSubject("Weekly Product Performance Report - " + periodStr);
        
        // Set email body content
        notification.setBodyContent(createEmailBody(report));
        
        // Set attachment path
        notification.setAttachmentPath(report.getFilePath());
        
        // Set scheduling
        notification.setScheduledSendTime(LocalDateTime.now());
        
        // Set email parameters
        notification.setRetryCount(0);
        notification.setMaxRetries(3);
        
        return notification;
    }

    private String createEmailBody(Report report) {
        StringBuilder body = new StringBuilder();
        
        body.append("Dear Sales Team,\n\n");
        body.append("Please find attached the weekly product performance report.\n\n");
        
        if (report.getSummary() != null && !report.getSummary().trim().isEmpty()) {
            body.append(report.getSummary());
        } else {
            body.append("Report Summary:\n");
            body.append("- Report Period: ").append(report.getReportPeriodStart())
                .append(" to ").append(report.getReportPeriodEnd()).append("\n");
            body.append("- Total Products Analyzed: ").append(report.getTotalProducts()).append("\n");
            
            if (report.getKeyInsights() != null && !report.getKeyInsights().isEmpty()) {
                body.append("\nKey Insights:\n");
                for (String insight : report.getKeyInsights()) {
                    body.append("- ").append(insight).append("\n");
                }
            }
        }
        
        body.append("\n\nBest regards,\n");
        body.append("Product Performance Analysis System");
        
        return body.toString();
    }
}
