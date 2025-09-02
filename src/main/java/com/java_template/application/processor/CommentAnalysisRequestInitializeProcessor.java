package com.java_template.application.processor;

import com.java_template.application.entity.commentanalysisrequest.version_1.CommentAnalysisRequest;
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
import java.util.UUID;

@Component
public class CommentAnalysisRequestInitializeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisRequestInitializeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CommentAnalysisRequestInitializeProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysisRequest initialization for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CommentAnalysisRequest.class)
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

    private boolean isValidEntity(CommentAnalysisRequest entity) {
        return entity != null && entity.isValid();
    }

    private CommentAnalysisRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CommentAnalysisRequest> context) {
        CommentAnalysisRequest entity = context.entity();

        // Generate unique requestId using UUID
        if (entity.getRequestId() == null || entity.getRequestId().trim().isEmpty()) {
            entity.setRequestId(UUID.randomUUID().toString());
        }

        // Set requestedAt to current timestamp
        entity.setRequestedAt(LocalDateTime.now());

        // Validate postId is positive integer
        if (entity.getPostId() == null || entity.getPostId() <= 0) {
            throw new IllegalArgumentException("PostId must be a positive integer");
        }

        // Validate recipientEmail format
        if (entity.getRecipientEmail() == null || 
            entity.getRecipientEmail().trim().isEmpty() || 
            !entity.getRecipientEmail().contains("@")) {
            throw new IllegalArgumentException("RecipientEmail must be a valid email address");
        }

        logger.info("Initialized CommentAnalysisRequest with requestId: {} for postId: {}", 
                   entity.getRequestId(), entity.getPostId());

        return entity;
    }
}
