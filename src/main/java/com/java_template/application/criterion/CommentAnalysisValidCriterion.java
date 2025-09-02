package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.commentanalysis.version_1.CommentAnalysis;
import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CommentAnalysisValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CommentAnalysisValidCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(CommentAnalysis.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<CommentAnalysis> context) {
        CommentAnalysis entity = context.entity();
        
        try {
            // Check if totalComments is greater than 0
            if (entity.getTotalComments() == null || entity.getTotalComments() <= 0) {
                return EvaluationOutcome.fail("totalComments must be greater than 0", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if averageCommentLength is a positive number
            if (entity.getAverageCommentLength() == null || entity.getAverageCommentLength() < 0) {
                return EvaluationOutcome.fail("averageCommentLength must be a positive number", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if uniqueAuthors is greater than 0
            if (entity.getUniqueAuthors() == null || entity.getUniqueAuthors() <= 0) {
                return EvaluationOutcome.fail("uniqueAuthors must be greater than 0", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if topKeywords is valid JSON and not empty
            if (entity.getTopKeywords() == null || entity.getTopKeywords().trim().isEmpty()) {
                return EvaluationOutcome.fail("topKeywords cannot be empty", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            try {
                JsonNode keywordsNode = objectMapper.readTree(entity.getTopKeywords());
                if (!keywordsNode.isObject() || keywordsNode.size() == 0) {
                    return EvaluationOutcome.fail("topKeywords must be a valid non-empty JSON object", 
                                                StandardEvalReasonCategories.VALIDATION_FAILURE);
                }
            } catch (Exception e) {
                return EvaluationOutcome.fail("topKeywords is not valid JSON: " + e.getMessage(), 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if sentimentSummary is not empty
            if (entity.getSentimentSummary() == null || entity.getSentimentSummary().trim().isEmpty()) {
                return EvaluationOutcome.fail("sentimentSummary cannot be empty", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if analysisCompletedAt timestamp is set
            if (entity.getAnalysisCompletedAt() == null) {
                return EvaluationOutcome.fail("analysisCompletedAt timestamp must be set", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Validate that totalComments matches actual count of Comment entities
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

            int actualCommentCount = commentResponses.size();
            if (!entity.getTotalComments().equals(actualCommentCount)) {
                return EvaluationOutcome.fail(
                    String.format("totalComments (%d) does not match actual comment count (%d)", 
                                entity.getTotalComments(), actualCommentCount), 
                    StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            logger.info("CommentAnalysis validation passed for analysisId: {}", entity.getAnalysisId());
            return EvaluationOutcome.success();
            
        } catch (Exception e) {
            logger.error("Error validating CommentAnalysis for analysisId: {}", entity.getAnalysisId(), e);
            return EvaluationOutcome.fail("Error validating CommentAnalysis: " + e.getMessage(), 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}
