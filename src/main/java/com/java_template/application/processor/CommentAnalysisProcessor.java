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

import java.util.List;
import java.util.UUID;

/**
 * CommentAnalysisProcessor - Marks comment as part of analysis batch and triggers analysis if needed
 * 
 * Transition: ingested → analyzed
 * Purpose: Mark comment as part of analysis batch and create CommentAnalysis if needed
 */
@Component
public class CommentAnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CommentAnalysisProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Comment analysis marking for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Comment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid comment entity")
                .map(this::processCommentAnalysisMarking)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Comment> entityWithMetadata) {
        Comment entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for marking comment for analysis
     */
    private EntityWithMetadata<Comment> processCommentAnalysisMarking(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Comment> context) {

        EntityWithMetadata<Comment> entityWithMetadata = context.entityResponse();
        Comment comment = entityWithMetadata.entity();

        logger.debug("Processing comment analysis marking: {}", comment.getCommentId());

        Integer postId = comment.getPostId();

        // Search for existing CommentAnalysis for this postId
        ModelSpec analysisModelSpec = new ModelSpec()
                .withName(CommentAnalysis.ENTITY_NAME)
                .withVersion(CommentAnalysis.ENTITY_VERSION);

        SimpleCondition postIdCondition = new SimpleCondition()
                .withJsonPath("$.postId")
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree(postId));

        GroupCondition condition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(postIdCondition));

        List<EntityWithMetadata<CommentAnalysis>> existingAnalyses = 
                entityService.search(analysisModelSpec, condition, CommentAnalysis.class);

        // If no CommentAnalysis exists, create one
        if (existingAnalyses.isEmpty()) {
            CommentAnalysis newAnalysis = new CommentAnalysis();
            newAnalysis.setAnalysisId(UUID.randomUUID().toString());
            newAnalysis.setPostId(postId);
            newAnalysis.setTotalComments(0);
            newAnalysis.setEmailSent(false);

            EntityWithMetadata<CommentAnalysis> createdAnalysis = entityService.create(newAnalysis);
            logger.info("Created new CommentAnalysis for postId: {} with ID: {}", 
                       postId, createdAnalysis.metadata().getId());
        }

        logger.info("Comment marked for analysis: {}", comment.getCommentId());

        return entityWithMetadata;
    }
}
