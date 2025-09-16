package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.application.entity.comment_analysis.version_1.CommentAnalysis;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * CommentAnalysisCalculationProcessor - Perform statistical analysis on collected comments
 * 
 * Transition: collecting → processing
 * Purpose: Perform statistical analysis on collected comments
 */
@Component
public class CommentAnalysisCalculationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisCalculationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CommentAnalysisCalculationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysis calculation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CommentAnalysis.class)
                .validate(this::isValidEntityWithMetadata, "Invalid comment analysis entity")
                .map(this::processAnalysisCalculation)
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
     * Main business logic for analysis calculation
     */
    private EntityWithMetadata<CommentAnalysis> processAnalysisCalculation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CommentAnalysis> context) {

        EntityWithMetadata<CommentAnalysis> entityWithMetadata = context.entityResponse();
        CommentAnalysis analysis = entityWithMetadata.entity();

        logger.debug("Calculating analysis for postId: {}", analysis.getPostId());

        Integer postId = analysis.getPostId();

        // Search for all analyzed comments for this postId
        ModelSpec commentModelSpec = new ModelSpec()
                .withName(Comment.ENTITY_NAME)
                .withVersion(Comment.ENTITY_VERSION);

        SimpleCondition postIdCondition = new SimpleCondition()
                .withJsonPath("$.postId")
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree(postId));

        GroupCondition condition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(postIdCondition));

        List<EntityWithMetadata<Comment>> comments = 
                entityService.search(commentModelSpec, condition, Comment.class);

        // Filter comments that are in "analyzed" state
        List<Comment> analyzedComments = comments.stream()
                .filter(commentWithMetadata -> "analyzed".equals(commentWithMetadata.metadata().getState()))
                .map(EntityWithMetadata::entity)
                .collect(Collectors.toList());

        analysis.setTotalComments(analyzedComments.size());

        if (!analyzedComments.isEmpty()) {
            calculateStatistics(analysis, analyzedComments);
        }

        logger.info("Analysis calculated for postId: {} with {} comments", postId, analyzedComments.size());

        return entityWithMetadata;
    }

    /**
     * Calculate statistical analysis from comments
     */
    private void calculateStatistics(CommentAnalysis analysis, List<Comment> comments) {
        // Calculate averages
        double totalWords = comments.stream()
                .mapToInt(comment -> comment.getWordCount() != null ? comment.getWordCount() : 0)
                .sum();
        double totalChars = comments.stream()
                .mapToInt(comment -> comment.getCharacterCount() != null ? comment.getCharacterCount() : 0)
                .sum();

        analysis.setAverageWordCount(totalWords / comments.size());
        analysis.setAverageCharacterCount(totalChars / comments.size());

        // Find most active commenter
        Map<String, Long> emailCounts = comments.stream()
                .collect(Collectors.groupingBy(Comment::getEmail, Collectors.counting()));
        
        String mostActiveEmail = emailCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        
        analysis.setMostActiveCommenter(mostActiveEmail);
        analysis.setUniqueCommenters(emailCounts.size());

        // Find longest and shortest comments
        Comment longestComment = comments.stream()
                .max(Comparator.comparing(comment -> comment.getWordCount() != null ? comment.getWordCount() : 0))
                .orElse(null);
        
        Comment shortestComment = comments.stream()
                .min(Comparator.comparing(comment -> comment.getWordCount() != null ? comment.getWordCount() : Integer.MAX_VALUE))
                .orElse(null);

        if (longestComment != null) {
            analysis.setLongestComment(createCommentSummary(longestComment));
        }
        
        if (shortestComment != null) {
            analysis.setShortestComment(createCommentSummary(shortestComment));
        }
    }

    /**
     * Create comment summary from comment
     */
    private CommentAnalysis.CommentSummary createCommentSummary(Comment comment) {
        CommentAnalysis.CommentSummary summary = new CommentAnalysis.CommentSummary();
        summary.setCommentId(comment.getCommentId());
        summary.setEmail(comment.getEmail());
        summary.setWordCount(comment.getWordCount());
        summary.setCharacterCount(comment.getCharacterCount());
        
        // Create body preview (first 100 characters)
        if (comment.getBody() != null) {
            String preview = comment.getBody().length() > 100 
                    ? comment.getBody().substring(0, 100) + "..."
                    : comment.getBody();
            summary.setBodyPreview(preview);
        }
        
        return summary;
    }
}
