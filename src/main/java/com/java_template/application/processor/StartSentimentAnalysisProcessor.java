package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.comment.version_1.Comment;
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
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Component
public class StartSentimentAnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartSentimentAnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartSentimentAnalysisProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for sentiment analysis request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.getPostId() != null && entity.getStatus() != null && "IN_PROGRESS".equals(entity.getStatus());
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Simulate fetching comments for the postId from storage
            List<Comment> comments = fetchCommentsForPost(job.getPostId());
            if (comments == null || comments.isEmpty()) {
                logger.error("No comments found for postId: {}", job.getPostId());
                job.setStatus("FAILED");
                return job;
            }

            // Perform sentiment analysis - simplistic example
            String sentimentSummary = analyzeSentiment(comments);
            String htmlReport = generateHtmlReport(sentimentSummary, comments);

            // Create CommentAnalysisReport entity - simulate persist
            CommentAnalysisReport report = new CommentAnalysisReport();
            report.setPostId(job.getPostId());
            report.setSentimentSummary(sentimentSummary);
            report.setHtmlReport(htmlReport);
            report.setCreatedAt(java.time.Instant.now().toString());

            logger.info("Generated CommentAnalysisReport for postId: {}", job.getPostId());

            // Update job status to report_generated
            job.setStatus("REPORT_GENERATED");

        } catch (Exception e) {
            logger.error("Exception during sentiment analysis", e);
            job.setStatus("FAILED");
        }
        return job;
    }

    private List<Comment> fetchCommentsForPost(Long postId) {
        // In real implementation, fetch from persistent storage
        // Here we simulate with dummy data or empty list
        return new ArrayList<>(); // Simulate empty for failure
    }

    private String analyzeSentiment(List<Comment> comments) {
        // Simple sentiment analysis: count positive words
        long positiveCount = comments.stream()
            .filter(c -> c.getBody() != null && c.getBody().toLowerCase().contains("good"))
            .count();
        if (positiveCount > comments.size() / 2) {
            return "Mostly positive";
        } else if (positiveCount > 0) {
            return "Mixed sentiments";
        } else {
            return "Mostly negative";
        }
    }

    private String generateHtmlReport(String sentimentSummary, List<Comment> comments) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h1>Comment Analysis Report</h1>");
        html.append("<p>Sentiment Summary: ").append(sentimentSummary).append("</p>");
        html.append("<ul>");
        for (Comment comment : comments) {
            html.append("<li>");
            html.append("<strong>").append(comment.getName()).append(":</strong> ");
            html.append(comment.getBody());
            html.append("</li>");
        }
        html.append("</ul>");
        html.append("</body></html>");
        return html.toString();
    }
}
