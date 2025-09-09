package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
import com.java_template.application.entity.commentanalysisreport.version_1.CommentAnalysisReport;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CommentAnalysisJobSendReportProcessor
 * 
 * Sends analysis report via email and completes the job.
 * This is a mock implementation that simulates email sending.
 */
@Component
public class CommentAnalysisJobSendReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisJobSendReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CommentAnalysisJobSendReportProcessor(SerializerFactory serializerFactory, 
                                               EntityService entityService,
                                               ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Sending report for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CommentAnalysisJob.class)
                .validate(this::isValidEntityWithMetadata, "Invalid CommentAnalysisJob")
                .map(this::processReportSending)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<CommentAnalysisJob> entityWithMetadata) {
        CommentAnalysisJob job = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return job != null && job.isValid() && technicalId != null;
    }

    private EntityWithMetadata<CommentAnalysisJob> processReportSending(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CommentAnalysisJob> context) {

        EntityWithMetadata<CommentAnalysisJob> entityWithMetadata = context.entityResponse();
        CommentAnalysisJob job = entityWithMetadata.entity();
        UUID jobId = entityWithMetadata.metadata().getId();

        logger.info("Sending report for job: {} to email: {}", jobId, job.getRecipientEmail());

        try {
            // Get CommentAnalysisReport for this job
            CommentAnalysisReport report = getReportForJob(job.getPostId().toString());
            
            if (report == null) {
                logger.error("No report found for job {}", jobId);
                job.setErrorMessage("No report found for email sending");
                return entityWithMetadata;
            }

            // Format and send email (mock implementation)
            sendEmailReport(job, report);
            
            // Update report sentAt timestamp
            report.setSentAt(LocalDateTime.now());
            
            // Find the report entity and update it
            EntityWithMetadata<CommentAnalysisReport> reportEntity = findReportEntity(job.getPostId().toString());
            if (reportEntity != null) {
                entityService.update(reportEntity.metadata().getId(), report, null);
            }
            
            // Set job completion timestamp
            job.setCompletedAt(LocalDateTime.now());
            
            logger.info("Successfully sent report for job {} to {}", jobId, job.getRecipientEmail());
            
        } catch (Exception e) {
            logger.error("Error sending report for job {}: {}", jobId, e.getMessage(), e);
            job.setErrorMessage("Email sending failed: " + e.getMessage());
        }

        return entityWithMetadata;
    }

    private CommentAnalysisReport getReportForJob(String jobId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CommentAnalysisReport.ENTITY_NAME).withVersion(CommentAnalysisReport.ENTITY_VERSION);
            
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.jobId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(jobId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<CommentAnalysisReport>> reports = entityService.search(modelSpec, condition, CommentAnalysisReport.class);
            
            return reports.isEmpty() ? null : reports.get(0).entity();
        } catch (Exception e) {
            logger.error("Error finding report for job {}", jobId, e);
            return null;
        }
    }

    private EntityWithMetadata<CommentAnalysisReport> findReportEntity(String jobId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(CommentAnalysisReport.ENTITY_NAME).withVersion(CommentAnalysisReport.ENTITY_VERSION);
            
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.jobId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(jobId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<CommentAnalysisReport>> reports = entityService.search(modelSpec, condition, CommentAnalysisReport.class);
            
            return reports.isEmpty() ? null : reports.get(0);
        } catch (Exception e) {
            logger.error("Error finding report entity for job {}", jobId, e);
            return null;
        }
    }

    private void sendEmailReport(CommentAnalysisJob job, CommentAnalysisReport report) {
        // Mock email sending implementation
        String subject = "Comment Analysis Report for Post " + job.getPostId();
        String emailBody = formatEmailBody(job, report);
        
        logger.info("MOCK EMAIL SENT:");
        logger.info("To: {}", job.getRecipientEmail());
        logger.info("Subject: {}", subject);
        logger.info("Body: {}", emailBody);
        
        // In a real implementation, you would use an email service like:
        // - Spring Mail
        // - AWS SES
        // - SendGrid
        // - etc.
    }

    private String formatEmailBody(CommentAnalysisJob job, CommentAnalysisReport report) {
        StringBuilder body = new StringBuilder();
        body.append("<html><body>");
        body.append("<h2>Comment Analysis Report</h2>");
        body.append("<p><strong>Post ID:</strong> ").append(job.getPostId()).append("</p>");
        body.append("<p><strong>Total Comments:</strong> ").append(report.getTotalComments()).append("</p>");
        body.append("<p><strong>Average Word Count:</strong> ").append(String.format("%.1f", report.getAverageWordCount())).append("</p>");
        body.append("<p><strong>Average Sentiment Score:</strong> ").append(String.format("%.2f", report.getAverageSentimentScore())).append("</p>");
        body.append("<h3>Sentiment Distribution:</h3>");
        body.append("<ul>");
        body.append("<li>Positive Comments: ").append(report.getPositiveCommentsCount()).append("</li>");
        body.append("<li>Negative Comments: ").append(report.getNegativeCommentsCount()).append("</li>");
        body.append("<li>Neutral Comments: ").append(report.getNeutralCommentsCount()).append("</li>");
        body.append("</ul>");
        body.append("<p><strong>Generated:</strong> ").append(report.getGeneratedAt()).append("</p>");
        body.append("</body></html>");
        return body.toString();
    }
}
