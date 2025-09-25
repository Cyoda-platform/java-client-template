package com.java_template.application.processor;

import com.java_template.application.entity.submission.version_1.Submission;
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
 * Processor for scientific review of submissions
 */
@Component
public class ScientificReviewProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScientificReviewProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ScientificReviewProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Submission.class)
                .validate(this::isValidEntityWithMetadata, "Invalid submission entity wrapper")
                .map(this::processScientificReview)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Submission> entityWithMetadata) {
        Submission entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Submission> processScientificReview(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Submission> context) {

        EntityWithMetadata<Submission> entityWithMetadata = context.entityResponse();
        Submission submission = entityWithMetadata.entity();

        logger.debug("Processing scientific review for submission: {}", submission.getSubmissionId());

        // Perform scientific review assessment
        performScientificAssessment(submission);
        
        // Update timestamps
        submission.setUpdatedAt(LocalDateTime.now());

        logger.info("Scientific review initiated for submission {}", submission.getSubmissionId());
        return entityWithMetadata;
    }

    private void performScientificAssessment(Submission submission) {
        // Assess scientific merit and methodology
        logger.info("Performing scientific assessment for submission: {}", submission.getSubmissionId());
        
        // Log therapeutic area for specialized review
        if (submission.getTherapeuticArea() != null) {
            logger.info("Therapeutic area: {} for submission {}", submission.getTherapeuticArea(), submission.getSubmissionId());
        }
        
        // Log study phase for appropriate review level
        if (submission.getPhase() != null) {
            logger.info("Study phase: {} for submission {}", submission.getPhase(), submission.getSubmissionId());
        }
    }
}
