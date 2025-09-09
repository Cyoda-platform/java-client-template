package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.comment.version_1.Comment;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * CommentAnalysisJobGenerateReportProcessor
 * 
 * Generates comprehensive analysis report with aggregated metrics.
 * Creates CommentAnalysisReport entity with calculated statistics.
 */
@Component
public class CommentAnalysisJobGenerateReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisJobGenerateReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CommentAnalysisJobGenerateReportProcessor(SerializerFactory serializerFactory, 
                                                   EntityService entityService,
                                                   ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Generating report for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CommentAnalysisJob.class)
                .validate(this::isValidEntityWithMetadata, "Invalid CommentAnalysisJob")
                .map(this::processReportGeneration)
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

    private EntityWithMetadata<CommentAnalysisJob> processReportGeneration(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CommentAnalysisJob> context) {

        EntityWithMetadata<CommentAnalysisJob> entityWithMetadata = context.entityResponse();
        CommentAnalysisJob job = entityWithMetadata.entity();
        UUID jobId = entityWithMetadata.metadata().getId();

        logger.info("Generating report for job: {}", jobId);

        try {
            // Get all Comment entities for this job
            List<EntityWithMetadata<Comment>> comments = getCommentsForJob(jobId.toString());
            
            if (comments.isEmpty()) {
                logger.warn("No comments found for job {}", jobId);
                job.setErrorMessage("No comments found for report generation");
                return entityWithMetadata;
            }

            // Calculate aggregated metrics
            CommentAnalysisReport report = generateReport(job, comments);
            
            // Create CommentAnalysisReport entity (will auto-transition to GENERATED state)
            entityService.create(report);
            
            logger.info("Successfully generated report for job {} with {} comments", jobId, comments.size());
            
        } catch (Exception e) {
            logger.error("Error generating report for job {}: {}", jobId, e.getMessage(), e);
            job.setErrorMessage("Report generation failed: " + e.getMessage());
        }

        return entityWithMetadata;
    }

    private List<EntityWithMetadata<Comment>> getCommentsForJob(String jobId) {
        ModelSpec modelSpec = new ModelSpec().withName(Comment.ENTITY_NAME).withVersion(Comment.ENTITY_VERSION);
        
        SimpleCondition simpleCondition = new SimpleCondition()
                .withJsonPath("$.jobId")
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree(jobId));

        GroupCondition condition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(simpleCondition));

        return entityService.search(modelSpec, condition, Comment.class);
    }

    private CommentAnalysisReport generateReport(CommentAnalysisJob job, List<EntityWithMetadata<Comment>> comments) {
        CommentAnalysisReport report = new CommentAnalysisReport();
        
        // Basic info
        report.setJobId(job.getPostId().toString()); // Using postId as business identifier
        report.setPostId(job.getPostId());
        report.setTotalComments(comments.size());
        report.setGeneratedAt(LocalDateTime.now());
        
        // Calculate metrics
        List<Comment> commentEntities = comments.stream()
                .map(EntityWithMetadata::entity)
                .collect(Collectors.toList());
        
        // Average word count
        double avgWordCount = commentEntities.stream()
                .mapToInt(c -> c.getWordCount() != null ? c.getWordCount() : 0)
                .average()
                .orElse(0.0);
        report.setAverageWordCount(avgWordCount);
        
        // Sentiment analysis
        double avgSentiment = commentEntities.stream()
                .mapToDouble(c -> c.getSentimentScore() != null ? c.getSentimentScore() : 0.0)
                .average()
                .orElse(0.0);
        report.setAverageSentimentScore(avgSentiment);
        
        // Sentiment counts
        int positiveCount = (int) commentEntities.stream()
                .filter(c -> c.getSentimentScore() != null && c.getSentimentScore() > 0.1)
                .count();
        int negativeCount = (int) commentEntities.stream()
                .filter(c -> c.getSentimentScore() != null && c.getSentimentScore() < -0.1)
                .count();
        int neutralCount = comments.size() - positiveCount - negativeCount;
        
        report.setPositiveCommentsCount(positiveCount);
        report.setNegativeCommentsCount(negativeCount);
        report.setNeutralCommentsCount(neutralCount);
        
        // Generate top commenters JSON
        report.setTopCommenters(generateTopCommenters(commentEntities));
        
        // Generate common keywords JSON
        report.setCommonKeywords(generateCommonKeywords(commentEntities));
        
        return report;
    }

    private String generateTopCommenters(List<Comment> comments) {
        try {
            Map<String, Long> commenterCounts = comments.stream()
                    .collect(Collectors.groupingBy(Comment::getEmail, Collectors.counting()));
            
            List<Map<String, Object>> topCommenters = commenterCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .map(entry -> {
                        Map<String, Object> commenter = new HashMap<>();
                        commenter.put("email", entry.getKey());
                        commenter.put("count", entry.getValue());
                        return commenter;
                    })
                    .collect(Collectors.toList());
            
            return objectMapper.writeValueAsString(topCommenters);
        } catch (Exception e) {
            logger.error("Error generating top commenters JSON", e);
            return "[]";
        }
    }

    private String generateCommonKeywords(List<Comment> comments) {
        try {
            Map<String, Long> wordCounts = new HashMap<>();
            Set<String> stopWords = Set.of("the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "is", "are", "was", "were", "be", "been", "have", "has", "had", "do", "does", "did", "will", "would", "could", "should", "may", "might", "can", "this", "that", "these", "those", "i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us", "them");
            
            for (Comment comment : comments) {
                if (comment.getBody() != null) {
                    String[] words = comment.getBody().toLowerCase()
                            .replaceAll("[^a-zA-Z\\s]", "")
                            .split("\\s+");
                    
                    for (String word : words) {
                        if (word.length() > 3 && !stopWords.contains(word)) {
                            wordCounts.merge(word, 1L, Long::sum);
                        }
                    }
                }
            }
            
            List<Map<String, Object>> commonKeywords = wordCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .map(entry -> {
                        Map<String, Object> keyword = new HashMap<>();
                        keyword.put("word", entry.getKey());
                        keyword.put("count", entry.getValue());
                        return keyword;
                    })
                    .collect(Collectors.toList());
            
            return objectMapper.writeValueAsString(commonKeywords);
        } catch (Exception e) {
            logger.error("Error generating common keywords JSON", e);
            return "[]";
        }
    }
}
