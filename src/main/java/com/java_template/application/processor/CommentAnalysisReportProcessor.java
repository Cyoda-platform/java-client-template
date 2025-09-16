package com.java_template.application.processor;

import com.java_template.application.entity.comment_analysis.version_1.CommentAnalysis;
import com.java_template.application.entity.email_report.version_1.EmailReport;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.util.UUID;

/**
 * CommentAnalysisReportProcessor - Create email report entity
 * 
 * Transition: completed → reported
 * Purpose: Create email report entity
 */
@Component
public class CommentAnalysisReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CommentAnalysisReportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysis report generation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CommentAnalysis.class)
                .validate(this::isValidEntityWithMetadata, "Invalid comment analysis entity")
                .map(this::processReportGeneration)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<CommentAnalysis> entityWithMetadata) {
        CommentAnalysis entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for report generation
     */
    private EntityWithMetadata<CommentAnalysis> processReportGeneration(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CommentAnalysis> context) {

        EntityWithMetadata<CommentAnalysis> entityWithMetadata = context.entityResponse();
        CommentAnalysis analysis = entityWithMetadata.entity();

        logger.debug("Generating report for analysis: {}", analysis.getAnalysisId());

        // Create new EmailReport entity
        EmailReport emailReport = new EmailReport();
        emailReport.setReportId(UUID.randomUUID().toString());
        emailReport.setAnalysisId(analysis.getAnalysisId());
        emailReport.setPostId(analysis.getPostId());
        emailReport.setRecipientEmail("admin@example.com"); // configurable
        emailReport.setSubject("Comment Analysis Report for Post " + analysis.getPostId());
        emailReport.setReportContent(generateReportContent(analysis));
        emailReport.setDeliveryStatus("PENDING");
        emailReport.setRetryCount(0);

        // Create the EmailReport entity (triggers EmailReport workflow: none → prepared)
        EntityWithMetadata<EmailReport> createdReport = entityService.create(emailReport);

        // Mark analysis as having email report created (but not sent yet)
        analysis.setEmailSent(false);

        logger.info("Email report created for analysis: {} with report ID: {}", 
                   analysis.getAnalysisId(), createdReport.metadata().getId());

        return entityWithMetadata;
    }

    /**
     * Generate HTML email content from analysis data
     */
    private String generateReportContent(CommentAnalysis analysis) {
        StringBuilder content = new StringBuilder();
        
        content.append("<html><body>");
        content.append("<h1>Comment Analysis Report</h1>");
        content.append("<h2>Post ID: ").append(analysis.getPostId()).append("</h2>");
        
        content.append("<h3>Summary</h3>");
        content.append("<ul>");
        content.append("<li>Total Comments: ").append(analysis.getTotalComments()).append("</li>");
        content.append("<li>Unique Commenters: ").append(analysis.getUniqueCommenters()).append("</li>");
        
        if (analysis.getAverageWordCount() != null) {
            content.append("<li>Average Word Count: ").append(String.format("%.2f", analysis.getAverageWordCount())).append("</li>");
        }
        
        if (analysis.getAverageCharacterCount() != null) {
            content.append("<li>Average Character Count: ").append(String.format("%.2f", analysis.getAverageCharacterCount())).append("</li>");
        }
        
        if (analysis.getMostActiveCommenter() != null) {
            content.append("<li>Most Active Commenter: ").append(analysis.getMostActiveCommenter()).append("</li>");
        }
        content.append("</ul>");
        
        // Longest comment details
        if (analysis.getLongestComment() != null) {
            CommentAnalysis.CommentSummary longest = analysis.getLongestComment();
            content.append("<h3>Longest Comment</h3>");
            content.append("<ul>");
            content.append("<li>Comment ID: ").append(longest.getCommentId()).append("</li>");
            content.append("<li>Email: ").append(longest.getEmail()).append("</li>");
            content.append("<li>Word Count: ").append(longest.getWordCount()).append("</li>");
            content.append("<li>Character Count: ").append(longest.getCharacterCount()).append("</li>");
            content.append("<li>Preview: ").append(longest.getBodyPreview()).append("</li>");
            content.append("</ul>");
        }
        
        // Shortest comment details
        if (analysis.getShortestComment() != null) {
            CommentAnalysis.CommentSummary shortest = analysis.getShortestComment();
            content.append("<h3>Shortest Comment</h3>");
            content.append("<ul>");
            content.append("<li>Comment ID: ").append(shortest.getCommentId()).append("</li>");
            content.append("<li>Email: ").append(shortest.getEmail()).append("</li>");
            content.append("<li>Word Count: ").append(shortest.getWordCount()).append("</li>");
            content.append("<li>Character Count: ").append(shortest.getCharacterCount()).append("</li>");
            content.append("<li>Preview: ").append(shortest.getBodyPreview()).append("</li>");
            content.append("</ul>");
        }
        
        if (analysis.getAnalysisCompletedAt() != null) {
            content.append("<p>Analysis completed at: ").append(analysis.getAnalysisCompletedAt()).append("</p>");
        }
        
        content.append("</body></html>");
        
        return content.toString();
    }
}
