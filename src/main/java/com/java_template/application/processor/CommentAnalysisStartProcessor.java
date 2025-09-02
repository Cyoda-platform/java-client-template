package com.java_template.application.processor;

import com.java_template.application.entity.commentanalysis.version_1.CommentAnalysis;
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
public class CommentAnalysisStartProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisStartProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CommentAnalysisStartProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysis start for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CommentAnalysis.class)
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

    private boolean isValidEntity(CommentAnalysis entity) {
        return entity != null && entity.isValid();
    }

    private CommentAnalysis processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CommentAnalysis> context) {
        CommentAnalysis entity = context.entity();

        // Set analysisStartedAt timestamp
        entity.setAnalysisStartedAt(LocalDateTime.now());
        
        // Initialize analysis metrics to zero
        entity.setTotalComments(0);
        entity.setAverageCommentLength(0.0);
        entity.setUniqueAuthors(0);
        entity.setTopKeywords("{}");
        entity.setSentimentSummary("Positive: 0, Negative: 0, Neutral: 0");

        logger.info("Started CommentAnalysis for analysisId: {} and requestId: {} at {}", 
                   entity.getAnalysisId(), entity.getRequestId(), entity.getAnalysisStartedAt());

        return entity;
    }
}
