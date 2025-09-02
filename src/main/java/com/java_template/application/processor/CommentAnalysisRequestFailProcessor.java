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

@Component
public class CommentAnalysisRequestFailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisRequestFailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CommentAnalysisRequestFailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysisRequest failure for request: {}", request.getId());

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
        return entity != null;
    }

    private CommentAnalysisRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CommentAnalysisRequest> context) {
        CommentAnalysisRequest entity = context.entity();

        // Set failedAt timestamp
        entity.setFailedAt(LocalDateTime.now());
        
        // Set failureReason from context (this would typically come from the error context)
        entity.setFailureReason("Processing failed - see logs for details");
        
        // Log failure details
        logger.error("CommentAnalysisRequest failed for requestId: {} at {}", 
                    entity.getRequestId(), entity.getFailedAt());

        return entity;
    }
}
