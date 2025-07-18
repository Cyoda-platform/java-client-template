package com.java_template.application.processor;

import com.java_template.application.entity.Comment;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class CommentProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public CommentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("CommentProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Comment for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Comment.class)
                .withErrorHandler(this::handleCommentError)
                .validate(Comment::isValid, "Invalid Comment entity state")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CommentProcessor".equals(modelSpec.operationName()) &&
                "comment".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ErrorInfo handleCommentError(Throwable t, Comment comment) {
        logger.error("Error processing Comment entity", t);
        return new ErrorInfo("CommentProcessingError", t.getMessage());
    }
}
