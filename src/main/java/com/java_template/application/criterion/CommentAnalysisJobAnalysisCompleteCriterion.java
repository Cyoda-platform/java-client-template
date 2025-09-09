package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
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
 * CommentAnalysisJobAnalysisCompleteCriterion
 * 
 * Checks if all comments have been analyzed (sentiment analysis complete).
 * Used in transition: ANALYZING → GENERATING_REPORT
 */
@Component
public class CommentAnalysisJobAnalysisCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CommentAnalysisJobAnalysisCompleteCriterion(SerializerFactory serializerFactory, 
                                                     EntityService entityService,
                                                     ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking analysis completion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(CommentAnalysisJob.class, this::validateAnalysisComplete)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateAnalysisComplete(CriterionSerializer.CriterionEntityEvaluationContext<CommentAnalysisJob> context) {
        CommentAnalysisJob job = context.entityWithMetadata().entity();

        if (job == null) {
            logger.warn("CommentAnalysisJob is null");
            return EvaluationOutcome.fail("Job is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        try {
            // Get count of Comment entities for this job
            String jobId = context.entityWithMetadata().metadata().getId().toString();
            List<EntityWithMetadata<Comment>> allComments = getCommentsForJob(jobId);
            
            if (allComments.isEmpty()) {
                logger.warn("No comments found for job {}", jobId);
                return EvaluationOutcome.fail("No comments found for analysis", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // Check if all comments are in ANALYZED state
            long analyzedCount = allComments.stream()
                    .filter(comment -> "ANALYZED".equals(comment.metadata().getState()))
                    .count();

            if (analyzedCount == allComments.size()) {
                logger.info("All {} comments analyzed for job {}", allComments.size(), jobId);
                return EvaluationOutcome.success();
            } else {
                logger.debug("Comments still being analyzed: {}/{} analyzed for job {}", 
                           analyzedCount, allComments.size(), jobId);
                return EvaluationOutcome.fail("Some comments still being analyzed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

        } catch (Exception e) {
            logger.error("Error checking analysis completion", e);
            return EvaluationOutcome.fail("Error checking analysis status: " + e.getMessage(),
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
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
}
