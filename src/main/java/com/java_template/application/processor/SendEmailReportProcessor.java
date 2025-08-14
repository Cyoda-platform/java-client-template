package com.java_template.application.processor;

import com.java_template.application.entity.commentanalysisreport.version_1.CommentAnalysisReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import static com.java_template.common.config.Config.*;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@Component
public class SendEmailReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public SendEmailReportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysisReport for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CommentAnalysisReport.class)
            .validate(this::isValidEntity, "Invalid CommentAnalysisReport entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CommentAnalysisReport entity) {
        return entity != null && entity.getPostId() != null && entity.getHtmlReport() != null;
    }

    private CommentAnalysisReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CommentAnalysisReport> context) {
        CommentAnalysisReport report = context.entity();
        try {
            logger.info("Sending email report for postId: {}", report.getPostId());

            // Fetch Job entity to update status after email sent
            CompletableFuture<Object> jobFuture = entityService.getItem(
                "Job",
                "1",
                UUID.fromString(context.getEvent().getId())
            );

            // Simulate sending email
            // In a real implementation, integrate with an email service provider here
            boolean emailSent = sendEmail(report.getHtmlReport());

            if (emailSent) {
                logger.info("Email sent successfully for postId: {}", report.getPostId());
                // In a real implementation, update job status to COMPLETED
                jobFuture.thenAccept(jobObj -> {
                    // Update status to COMPLETED
                    // Note: update operation not needed, change entity state only
                    // This is a simulation
                    logger.info("Job status set to COMPLETED for report postId: {}", report.getPostId());
                });
            } else {
                logger.error("Failed to send email for postId: {}", report.getPostId());
                jobFuture.thenAccept(jobObj -> {
                    logger.error("Setting job status to FAILED for postId: {}", report.getPostId());
                });
            }
        } catch (Exception e) {
            logger.error("Exception during sending email report", e);
        }
        return report;
    }

    private boolean sendEmail(String htmlContent) {
        // Placeholder for email sending logic
        // Always return true to simulate success
        return true;
    }
}
