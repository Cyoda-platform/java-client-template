package com.java_template.application.processor;

import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
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

import java.util.UUID;

/**
 * CommentAnalysisJobCompleteAnalysisProcessor
 * 
 * Marks analysis as complete and moves to report generation phase.
 * This processor is triggered when all comments have been analyzed.
 */
@Component
public class CommentAnalysisJobCompleteAnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisJobCompleteAnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CommentAnalysisJobCompleteAnalysisProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Completing analysis for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CommentAnalysisJob.class)
                .validate(this::isValidEntityWithMetadata, "Invalid CommentAnalysisJob")
                .map(this::processAnalysisCompletion)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<CommentAnalysisJob> entityWithMetadata) {
        CommentAnalysisJob job = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return job != null && job.isValid() && technicalId != null;
    }

    private EntityWithMetadata<CommentAnalysisJob> processAnalysisCompletion(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CommentAnalysisJob> context) {

        EntityWithMetadata<CommentAnalysisJob> entityWithMetadata = context.entityResponse();
        CommentAnalysisJob job = entityWithMetadata.entity();
        UUID jobId = entityWithMetadata.metadata().getId();

        logger.info("Completing analysis phase for job: {}", jobId);

        // Log completion of analysis phase
        logger.info("Analysis completed for job {} with {} comments", jobId, job.getTotalComments());

        return entityWithMetadata;
    }
}
