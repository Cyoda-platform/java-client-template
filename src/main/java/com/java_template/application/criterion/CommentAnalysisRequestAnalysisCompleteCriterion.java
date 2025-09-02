package com.java_template.application.criterion;

import com.java_template.application.entity.commentanalysisrequest.version_1.CommentAnalysisRequest;
import com.java_template.application.entity.commentanalysis.version_1.CommentAnalysis;
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
import java.util.Optional;

@Component
public class CommentAnalysisRequestAnalysisCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public CommentAnalysisRequestAnalysisCompleteCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(CommentAnalysisRequest.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<CommentAnalysisRequest> context) {
        CommentAnalysisRequest entity = context.entity();
        
        try {
            // Get CommentAnalysis entity by requestId
            Condition requestIdCondition = Condition.of("$.requestId", "EQUALS", entity.getRequestId());
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(requestIdCondition));

            Optional<com.java_template.common.dto.EntityResponse<CommentAnalysis>> analysisResponse = 
                entityService.getFirstItemByCondition(
                    CommentAnalysis.class, 
                    CommentAnalysis.ENTITY_NAME, 
                    CommentAnalysis.ENTITY_VERSION, 
                    condition, 
                    true
                );

            // Check if entity exists
            if (!analysisResponse.isPresent()) {
                return EvaluationOutcome.fail("CommentAnalysis not found for requestId: " + entity.getRequestId(), 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            CommentAnalysis analysis = analysisResponse.get().getData();
            String entityState = analysisResponse.get().getMetadata().getState();

            // Check if entity state is "completed"
            if (!"completed".equals(entityState)) {
                return EvaluationOutcome.fail("CommentAnalysis state is not completed, current state: " + entityState, 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if analysisCompletedAt timestamp is set
            if (analysis.getAnalysisCompletedAt() == null) {
                return EvaluationOutcome.fail("CommentAnalysis analysisCompletedAt timestamp is not set", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if all required analysis fields are populated
            if (analysis.getTotalComments() == null || analysis.getTotalComments() <= 0) {
                return EvaluationOutcome.fail("CommentAnalysis totalComments is not valid", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (analysis.getAverageCommentLength() == null) {
                return EvaluationOutcome.fail("CommentAnalysis averageCommentLength is not set", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (analysis.getUniqueAuthors() == null || analysis.getUniqueAuthors() <= 0) {
                return EvaluationOutcome.fail("CommentAnalysis uniqueAuthors is not valid", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (analysis.getTopKeywords() == null || analysis.getTopKeywords().trim().isEmpty()) {
                return EvaluationOutcome.fail("CommentAnalysis topKeywords is empty", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (analysis.getSentimentSummary() == null || analysis.getSentimentSummary().trim().isEmpty()) {
                return EvaluationOutcome.fail("CommentAnalysis sentimentSummary is empty", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            logger.info("CommentAnalysis is complete for requestId: {}", entity.getRequestId());
            return EvaluationOutcome.success();
            
        } catch (Exception e) {
            logger.error("Error checking analysis completion for requestId: {}", entity.getRequestId(), e);
            return EvaluationOutcome.fail("Error checking analysis completion: " + e.getMessage(), 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}
