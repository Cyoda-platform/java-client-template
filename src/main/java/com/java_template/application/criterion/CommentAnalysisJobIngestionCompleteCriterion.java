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
 * CommentAnalysisJobIngestionCompleteCriterion
 * 
 * Checks if all comments have been successfully ingested from the API.
 * Used in transition: INGESTING → ANALYZING
 */
@Component
public class CommentAnalysisJobIngestionCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CommentAnalysisJobIngestionCompleteCriterion(SerializerFactory serializerFactory, 
                                                      EntityService entityService,
                                                      ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ingestion completion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(CommentAnalysisJob.class, this::validateIngestionComplete)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateIngestionComplete(CriterionSerializer.CriterionEntityEvaluationContext<CommentAnalysisJob> context) {
        CommentAnalysisJob job = context.entityWithMetadata().entity();

        if (job == null) {
            logger.warn("CommentAnalysisJob is null");
            return EvaluationOutcome.fail("Job is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if totalComments is set (ingestion started)
        if (job.getTotalComments() == null) {
            logger.debug("Ingestion not yet started for job with postId: {}", job.getPostId());
            return EvaluationOutcome.fail("Ingestion not yet started", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        try {
            // Get count of Comment entities for this job
            String jobId = context.entityWithMetadata().metadata().getId().toString();
            List<EntityWithMetadata<Comment>> allComments = getCommentsForJob(jobId);
            
            if (allComments.size() < job.getTotalComments()) {
                logger.debug("Still ingesting comments: {}/{} for job {}", 
                           allComments.size(), job.getTotalComments(), jobId);
                return EvaluationOutcome.fail("Still ingesting comments", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // Check if all comments are in INGESTED state
            long ingestedCount = allComments.stream()
                    .filter(comment -> "INGESTED".equals(comment.metadata().getState()))
                    .count();

            if (ingestedCount == job.getTotalComments()) {
                logger.info("All {} comments ingested for job {}", job.getTotalComments(), jobId);
                return EvaluationOutcome.success();
            } else {
                logger.debug("Comments still processing: {}/{} ingested for job {}", 
                           ingestedCount, job.getTotalComments(), jobId);
                return EvaluationOutcome.fail("Some comments still processing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

        } catch (Exception e) {
            logger.error("Error checking ingestion completion", e);
            return EvaluationOutcome.fail("Error checking ingestion status: " + e.getMessage(),
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
