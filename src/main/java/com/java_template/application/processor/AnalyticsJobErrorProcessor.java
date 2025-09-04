package com.java_template.application.processor;

import com.java_template.application.entity.analyticsjob.version_1.AnalyticsJob;
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

import java.time.LocalDateTime;
import java.time.Duration;

@Component
public class AnalyticsJobErrorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsJobErrorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AnalyticsJobErrorProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AnalyticsJob error handling for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AnalyticsJob.class)
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

    private boolean isValidEntity(AnalyticsJob entity) {
        return entity != null && entity.isValid();
    }

    private AnalyticsJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AnalyticsJob> context) {
        AnalyticsJob job = context.entity();

        try {
            job.setCompletedAt(LocalDateTime.now());

            // Log failure details
            Duration duration = job.getStartedAt() != null ? 
                Duration.between(job.getStartedAt(), job.getCompletedAt()) : Duration.ZERO;
            
            logger.error("Job failed: {} (Duration: {} seconds, Error: {})",
                job.getJobId(),
                duration.getSeconds(),
                job.getErrorMessage());

            // Send failure notification
            sendFailureNotification(job);

        } catch (Exception e) {
            logger.error("Failed to process job error for {}: {}", job.getJobId(), e.getMessage(), e);
            // Don't throw exception here as we want to complete the error processing
        }

        return job;
    }

    private void sendFailureNotification(AnalyticsJob job) {
        try {
            String emailSubject = "Analytics Job Failed - " + job.getJobId();
            StringBuilder emailBody = new StringBuilder();
            emailBody.append("The analytics job ").append(job.getJobId())
                     .append(" failed with error: ").append(job.getErrorMessage()).append("\n\n");
            
            emailBody.append("Job Details:\n");
            emailBody.append("- Job ID: ").append(job.getJobId()).append("\n");
            emailBody.append("- Job Type: ").append(job.getJobType()).append("\n");
            emailBody.append("- Scheduled For: ").append(job.getScheduledFor()).append("\n");
            emailBody.append("- Started At: ").append(job.getStartedAt()).append("\n");
            emailBody.append("- Failed At: ").append(job.getCompletedAt()).append("\n");
            emailBody.append("- Books Processed: ").append(job.getBooksProcessed()).append("\n");
            emailBody.append("- Reports Generated: ").append(job.getReportsGenerated()).append("\n\n");
            
            emailBody.append("Please investigate the issue and consider retrying the job if appropriate.\n");
            emailBody.append("You can retry the job using the 'retry_failed_job' transition.\n\n");
            emailBody.append("Best regards,\nBook Analytics System");

            // Simulate sending email to admin
            sendEmail("admin@company.com", emailSubject, emailBody.toString());
            
            logger.info("Failure notification sent for job: {}", job.getJobId());

        } catch (Exception e) {
            logger.error("Failed to send failure notification for job {}: {}", job.getJobId(), e.getMessage(), e);
        }
    }

    private void sendEmail(String recipient, String subject, String body) {
        // Simulate email sending - in a real implementation, this would integrate with an email service
        logger.info("=== FAILURE NOTIFICATION EMAIL ===");
        logger.info("To: {}", recipient);
        logger.info("Subject: {}", subject);
        logger.info("Body:\n{}", body);
        logger.info("=== END FAILURE NOTIFICATION EMAIL ===");
        
        // Simulate email sending delay
        try {
            Thread.sleep(50); // 50ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Email sending interrupted");
        }
        
        logger.info("Failure notification email sent to {}", recipient);
    }
}
