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
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import static com.java_template.common.config.Config.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.UUID;

@Component
public class StartSentimentAnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartSentimentAnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public StartSentimentAnalysisProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
            // Fetch comments for the postId from persistent storage
            CompletableFuture<ArrayNode> commentsFuture = entityService.getItemsByCondition(
                Comment.ENTITY_NAME,
                String.valueOf(Comment.ENTITY_VERSION),
                SearchConditionRequest.group("AND",
                    com.java_template.common.util.Condition.of("$.postId", "EQUALS", job.getPostId())
                ),
                true
            );

            ArrayNode commentsNode = commentsFuture.get();
            if (commentsNode == null || commentsNode.isEmpty()) {
                logger.error("No comments found for postId: {}", job.getPostId());
                job.setStatus("FAILED");
                return job;
            }

            // Deserialize commentsNode to List<Comment>
            List<Comment> comments = new ArrayList<>();
            for (int i = 0; i < commentsNode.size(); i++) {
                ObjectNode commentNode = (ObjectNode) commentsNode.get(i);
                Comment comment = objectMapper.convertValue(commentNode, Comment.class);
                comments.add(comment);
            }

            // Perform sentiment analysis
            String sentimentSummary = analyzeSentiment(comments);
            String htmlReport = generateHtmlReport(sentimentSummary, comments);

            // Create CommentAnalysisReport entity
            CommentAnalysisReport report = new CommentAnalysisReport();
            report.setPostId(job.getPostId());
            report.setSentimentSummary(sentimentSummary);
            report.setHtmlReport(htmlReport);
            report.setCreatedAt(java.time.Instant.now().toString());

            CompletableFuture<java.util.UUID> reportFuture = entityService.addItem(
                CommentAnalysisReport.ENTITY_NAME,
                String.valueOf(CommentAnalysisReport.ENTITY_VERSION),
                report
            );

            reportFuture.whenComplete((id, ex) -> {
                if (ex != null) {
                    logger.error("Failed to persist CommentAnalysisReport for postId: {}", job.getPostId(), ex);
                    job.setStatus("FAILED");
                } else {
                    logger.info("Persisted CommentAnalysisReport with id: {}", id);
                    // Update job status to REPORT_GENERATED
                    job.setStatus("REPORT_GENERATED");
                }
            });

        } catch (Exception e) {
            logger.error("Exception during sentiment analysis", e);
            job.setStatus("FAILED");
        }
        return job;
    }

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private String analyzeSentiment(List<Comment> comments) {
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
