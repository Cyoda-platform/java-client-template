package com.java_template.application.processor;

import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class CommentFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CommentFetchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Comment fetch for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Comment.class)
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

    private boolean isValidEntity(Comment entity) {
        return entity != null && entity.isValid();
    }

    private Comment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Comment> context) {
        Comment entity = context.entity();

        // Set fetchedAt to current timestamp
        entity.setFetchedAt(LocalDateTime.now());
        
        // Validate comment data completeness
        if (entity.getCommentId() == null || entity.getCommentId() <= 0) {
            throw new IllegalArgumentException("Comment ID must be a positive integer");
        }
        
        if (entity.getPostId() == null || entity.getPostId() <= 0) {
            throw new IllegalArgumentException("Post ID must be a positive integer");
        }
        
        if (entity.getName() == null || entity.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Comment name cannot be empty");
        }
        
        if (entity.getEmail() == null || entity.getEmail().trim().isEmpty() || !entity.getEmail().contains("@")) {
            throw new IllegalArgumentException("Comment email must be a valid email address");
        }
        
        if (entity.getBody() == null || entity.getBody().trim().isEmpty()) {
            throw new IllegalArgumentException("Comment body cannot be empty");
        }
        
        if (entity.getRequestId() == null || entity.getRequestId().trim().isEmpty()) {
            throw new IllegalArgumentException("Request ID cannot be empty");
        }

        logger.info("Successfully fetched Comment with commentId: {} for requestId: {}", 
                   entity.getCommentId(), entity.getRequestId());

        return entity;
    }
}
