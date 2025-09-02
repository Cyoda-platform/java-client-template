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
public class CommentProcessProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentProcessProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CommentProcessProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Comment process for request: {}", request.getId());

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

        // Mark comment as available for analysis
        // Set processedAt timestamp
        entity.setProcessedAt(LocalDateTime.now());

        logger.info("Successfully processed Comment with commentId: {} for requestId: {} at {}", 
                   entity.getCommentId(), entity.getRequestId(), entity.getProcessedAt());

        return entity;
    }
}
