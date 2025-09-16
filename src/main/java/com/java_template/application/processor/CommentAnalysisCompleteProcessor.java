package com.java_template.application.processor;

import com.java_template.application.entity.comment_analysis.version_1.CommentAnalysis;
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
 * CommentAnalysisCompleteProcessor - Finalize analysis results
 * 
 * Transition: processing → completed
 * Purpose: Finalize analysis results
 */
@Component
public class CommentAnalysisCompleteProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisCompleteProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CommentAnalysisCompleteProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysis completion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CommentAnalysis.class)
                .validate(this::isValidEntityWithMetadata, "Invalid comment analysis entity")
                .map(this::processAnalysisCompletion)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<CommentAnalysis> entityWithMetadata) {
        CommentAnalysis entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for completing analysis
     */
    private EntityWithMetadata<CommentAnalysis> processAnalysisCompletion(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CommentAnalysis> context) {

        EntityWithMetadata<CommentAnalysis> entityWithMetadata = context.entityResponse();
        CommentAnalysis analysis = entityWithMetadata.entity();

        logger.debug("Completing analysis for postId: {}", analysis.getPostId());

        // Set completion timestamp
        analysis.setAnalysisCompletedAt(LocalDateTime.now());

        logger.info("Analysis completed for postId: {}", analysis.getPostId());

        return entityWithMetadata;
    }
}
