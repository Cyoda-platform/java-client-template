package com.java_template.application.processor;

import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * CommentAnalyzeProcessor
 * 
 * Analyzes comment sentiment and extracts additional metrics.
 * Performs simple keyword-based sentiment scoring.
 */
@Component
public class CommentAnalyzeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalyzeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // Simple sentiment word lists
    private static final List<String> POSITIVE_WORDS = Arrays.asList(
        "good", "great", "excellent", "amazing", "wonderful", "fantastic", "awesome", "love", "like", 
        "happy", "pleased", "satisfied", "perfect", "brilliant", "outstanding", "superb", "nice",
        "beautiful", "helpful", "useful", "valuable", "impressive", "remarkable", "incredible"
    );

    private static final List<String> NEGATIVE_WORDS = Arrays.asList(
        "bad", "terrible", "awful", "horrible", "disgusting", "hate", "dislike", "angry", "frustrated",
        "disappointed", "annoyed", "upset", "sad", "poor", "worst", "useless", "worthless", "stupid",
        "ridiculous", "pathetic", "boring", "confusing", "difficult", "problem", "issue", "error"
    );

    public CommentAnalyzeProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Analyzing comment for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Comment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid Comment")
                .map(this::processCommentAnalysis)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Comment> entityWithMetadata) {
        Comment comment = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return comment != null && comment.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Comment> processCommentAnalysis(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Comment> context) {

        EntityWithMetadata<Comment> entityWithMetadata = context.entityResponse();
        Comment comment = entityWithMetadata.entity();
        UUID commentId = entityWithMetadata.metadata().getId();

        logger.debug("Analyzing sentiment for comment: {}", commentId);

        // Perform sentiment analysis on comment body
        double sentimentScore = calculateSentimentScore(comment.getBody());
        comment.setSentimentScore(sentimentScore);

        logger.debug("Comment {} analyzed with sentiment score: {}", commentId, sentimentScore);

        return entityWithMetadata;
    }

    private double calculateSentimentScore(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }

        String lowerText = text.toLowerCase();
        String[] words = lowerText.split("\\s+");
        
        int positiveCount = 0;
        int negativeCount = 0;
        
        for (String word : words) {
            // Remove punctuation
            word = word.replaceAll("[^a-zA-Z]", "");
            
            if (POSITIVE_WORDS.contains(word)) {
                positiveCount++;
            } else if (NEGATIVE_WORDS.contains(word)) {
                negativeCount++;
            }
        }
        
        // Calculate normalized sentiment score between -1 and 1
        int totalSentimentWords = positiveCount + negativeCount;
        if (totalSentimentWords == 0) {
            return 0.0; // Neutral
        }
        
        double rawScore = (double) (positiveCount - negativeCount) / totalSentimentWords;
        
        // Normalize to range [-1, 1]
        return Math.max(-1.0, Math.min(1.0, rawScore));
    }
}
