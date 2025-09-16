package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.application.entity.comment_analysis.version_1.CommentAnalysis;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CommentAnalysisReadyCriterion - Check if analysis has sufficient comments to proceed with processing
 * 
 * Transition: collecting → processing (begin_processing)
 * Purpose: Check if analysis has sufficient comments to proceed with processing
 */
@Component
public class CommentAnalysisReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CommentAnalysisReadyCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking CommentAnalysis readiness criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(CommentAnalysis.class, this::validateAnalysisReadiness)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for analysis readiness
     */
    private EvaluationOutcome validateAnalysisReadiness(CriterionSerializer.CriterionEntityEvaluationContext<CommentAnalysis> context) {
        CommentAnalysis analysis = context.entityWithMetadata().entity();

        // Check if analysis entity is null
        if (analysis == null) {
            logger.warn("CommentAnalysis entity is null");
            return EvaluationOutcome.fail("Analysis entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!analysis.isValid()) {
            logger.warn("CommentAnalysis entity is not valid");
            return EvaluationOutcome.fail("Analysis entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        Integer postId = analysis.getPostId();
        if (postId == null) {
            logger.warn("PostId is null in CommentAnalysis");
            return EvaluationOutcome.fail("PostId cannot be null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Search for analyzed comments for this postId
        try {
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

            // Count comments that are in "analyzed" state
            long analyzedCommentCount = comments.stream()
                    .filter(commentWithMetadata -> "analyzed".equals(commentWithMetadata.metadata().getState()))
                    .count();

            if (analyzedCommentCount >= 1) {
                logger.debug("Analysis ready: found {} analyzed comments for postId {}", analyzedCommentCount, postId);
                return EvaluationOutcome.success();
            } else {
                logger.debug("Analysis not ready: only {} analyzed comments for postId {}", analyzedCommentCount, postId);
                return EvaluationOutcome.fail(
                    String.format("Not enough analyzed comments for postId %d. Found: %d, Required: 1", postId, analyzedCommentCount),
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                );
            }

        } catch (Exception e) {
            logger.error("Error checking comment readiness for postId: {}", postId, e);
            return EvaluationOutcome.fail(
                "Error checking comment availability: " + e.getMessage(),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }
    }
}
