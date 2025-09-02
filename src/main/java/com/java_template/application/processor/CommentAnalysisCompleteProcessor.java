package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.commentanalysis.version_1.CommentAnalysis;
import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class CommentAnalysisCompleteProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisCompleteProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // Common stop words to filter out
    private static final Set<String> STOP_WORDS = Set.of(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "is", "are", "was", "were", "be", "been", "have", "has", "had", "do", "does", "did", "will", "would", "could", "should", "may", "might", "must", "can", "shall", "this", "that", "these", "those", "i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us", "them", "my", "your", "his", "her", "its", "our", "their"
    );

    // Positive sentiment words
    private static final Set<String> POSITIVE_WORDS = Set.of(
        "good", "great", "excellent", "amazing", "wonderful", "fantastic", "awesome", "brilliant", "perfect", "outstanding", "superb", "magnificent", "marvelous", "terrific", "fabulous", "incredible", "remarkable", "exceptional", "splendid", "lovely", "beautiful", "nice", "pleasant", "delightful", "charming", "enjoyable", "satisfying", "impressive", "admirable", "commendable"
    );

    // Negative sentiment words
    private static final Set<String> NEGATIVE_WORDS = Set.of(
        "bad", "terrible", "awful", "horrible", "disgusting", "disappointing", "poor", "worst", "hate", "dislike", "annoying", "frustrating", "useless", "worthless", "pathetic", "ridiculous", "stupid", "dumb", "ugly", "nasty", "rude", "mean", "cruel", "harsh", "unfair", "wrong", "false", "fake", "lies", "boring", "dull"
    );

    public CommentAnalysisCompleteProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysis complete for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CommentAnalysis.class)
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

    private boolean isValidEntity(CommentAnalysis entity) {
        return entity != null && entity.isValid();
    }

    private CommentAnalysis processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CommentAnalysis> context) {
        CommentAnalysis entity = context.entity();

        try {
            // Get all Comment entities by requestId
            Condition requestIdCondition = Condition.of("$.requestId", "EQUALS", entity.getRequestId());
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(requestIdCondition));

            List<com.java_template.common.dto.EntityResponse<Comment>> commentResponses = 
                entityService.getItemsByCondition(
                    Comment.class, 
                    Comment.ENTITY_NAME, 
                    Comment.ENTITY_VERSION, 
                    condition, 
                    true
                );

            List<Comment> comments = commentResponses.stream()
                .map(response -> response.getData())
                .collect(Collectors.toList());

            if (comments.isEmpty()) {
                throw new RuntimeException("No comments found for requestId: " + entity.getRequestId());
            }

            // Calculate totalComments
            entity.setTotalComments(comments.size());

            // Calculate averageCommentLength
            double averageLength = comments.stream()
                .mapToInt(comment -> comment.getBody().length())
                .average()
                .orElse(0.0);
            entity.setAverageCommentLength(averageLength);

            // Calculate uniqueAuthors
            Set<String> uniqueEmails = comments.stream()
                .map(Comment::getEmail)
                .collect(Collectors.toSet());
            entity.setUniqueAuthors(uniqueEmails.size());

            // Extract and count keywords
            Map<String, Integer> keywordCounts = extractKeywords(comments);
            String topKeywordsJson = objectMapper.writeValueAsString(keywordCounts);
            entity.setTopKeywords(topKeywordsJson);

            // Perform sentiment analysis
            String sentimentSummary = performSentimentAnalysis(comments);
            entity.setSentimentSummary(sentimentSummary);

            // Set analysisCompletedAt timestamp
            entity.setAnalysisCompletedAt(LocalDateTime.now());

            logger.info("Completed analysis for requestId: {} with {} comments, {} unique authors", 
                       entity.getRequestId(), entity.getTotalComments(), entity.getUniqueAuthors());

        } catch (Exception e) {
            logger.error("Failed to complete analysis for requestId: {}", entity.getRequestId(), e);
            throw new RuntimeException("Failed to complete analysis: " + e.getMessage(), e);
        }

        return entity;
    }

    private Map<String, Integer> extractKeywords(List<Comment> comments) {
        Map<String, Integer> wordCounts = new HashMap<>();

        for (Comment comment : comments) {
            String[] words = comment.getBody().toLowerCase()
                .replaceAll("[^a-zA-Z\\s]", "")
                .split("\\s+");

            for (String word : words) {
                word = word.trim();
                if (word.length() > 2 && !STOP_WORDS.contains(word)) {
                    wordCounts.put(word, wordCounts.getOrDefault(word, 0) + 1);
                }
            }
        }

        // Return top 10 keywords
        return wordCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }

    private String performSentimentAnalysis(List<Comment> comments) {
        int positiveCount = 0;
        int negativeCount = 0;
        int neutralCount = 0;

        for (Comment comment : comments) {
            String body = comment.getBody().toLowerCase();
            boolean hasPositive = POSITIVE_WORDS.stream().anyMatch(body::contains);
            boolean hasNegative = NEGATIVE_WORDS.stream().anyMatch(body::contains);

            if (hasPositive && !hasNegative) {
                positiveCount++;
            } else if (hasNegative && !hasPositive) {
                negativeCount++;
            } else {
                neutralCount++;
            }
        }

        return String.format("Positive: %d, Negative: %d, Neutral: %d", 
                           positiveCount, negativeCount, neutralCount);
    }
}
