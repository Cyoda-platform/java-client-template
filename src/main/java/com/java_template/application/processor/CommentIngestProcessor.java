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

import java.time.LocalDateTime;

/**
 * CommentIngestProcessor - Validates and enriches comment data from JSONPlaceholder API
 * 
 * Transition: none → ingested
 * Purpose: Validate and enrich comment data with calculated fields
 */
@Component
public class CommentIngestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentIngestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CommentIngestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Comment ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Comment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid comment entity")
                .map(this::processCommentIngestion)
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
     * Main business logic for comment ingestion
     */
    private EntityWithMetadata<Comment> processCommentIngestion(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Comment> context) {

        EntityWithMetadata<Comment> entityWithMetadata = context.entityResponse();
        Comment comment = entityWithMetadata.entity();

        logger.debug("Processing comment ingestion: {}", comment.getCommentId());

        // Set ingestion timestamp
        comment.setIngestedAt(LocalDateTime.now());

        // Calculate word count
        if (comment.getBody() != null) {
            String[] words = comment.getBody().trim().split("\\s+");
            comment.setWordCount(words.length);
        } else {
            comment.setWordCount(0);
        }

        // Calculate character count
        if (comment.getBody() != null) {
            comment.setCharacterCount(comment.getBody().length());
        } else {
            comment.setCharacterCount(0);
        }

        logger.info("Comment ingested: {} with {} words and {} characters", 
                   comment.getCommentId(), comment.getWordCount(), comment.getCharacterCount());

        return entityWithMetadata;
    }
}
