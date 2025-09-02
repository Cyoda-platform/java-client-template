package com.java_template.application.criterion;

import com.java_template.application.entity.commentanalysisrequest.version_1.CommentAnalysisRequest;
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
public class CommentAnalysisRequestFetchCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public CommentAnalysisRequestFetchCompleteCriterion(SerializerFactory serializerFactory, EntityService entityService) {
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

            // Check if at least one comment exists
            if (commentResponses.isEmpty()) {
                return EvaluationOutcome.fail("No comments found for requestId: " + entity.getRequestId(), 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if all comments have fetchedAt timestamp set
            for (com.java_template.common.dto.EntityResponse<Comment> response : commentResponses) {
                Comment comment = response.getData();
                if (comment.getFetchedAt() == null) {
                    return EvaluationOutcome.fail("Comment " + comment.getCommentId() + " has not been fetched yet", 
                                                StandardEvalReasonCategories.VALIDATION_FAILURE);
                }
            }

            logger.info("All {} comments have been successfully fetched for requestId: {}", 
                       commentResponses.size(), entity.getRequestId());
            
            return EvaluationOutcome.success();
            
        } catch (Exception e) {
            logger.error("Error checking fetch completion for requestId: {}", entity.getRequestId(), e);
            return EvaluationOutcome.fail("Error checking fetch completion: " + e.getMessage(), 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}
