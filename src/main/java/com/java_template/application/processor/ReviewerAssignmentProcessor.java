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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ReviewerAssignmentProcessor - Handles reviewer assignment logic
 * Processes reviewer assignment to submissions
 */
@Component
public class ReviewerAssignmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReviewerAssignmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReviewerAssignmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Reviewer assignment for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Submission.class)
                .validate(this::isValidEntityWithMetadata, "Invalid submission entity wrapper")
                .map(this::processReviewerAssignmentLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Submission> entityWithMetadata) {
        Submission entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for reviewer assignment
     */
    private EntityWithMetadata<Submission> processReviewerAssignmentLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Submission> context) {

        EntityWithMetadata<Submission> entityWithMetadata = context.entityResponse();
        Submission submission = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing reviewer assignment for submission: {} in state: {}", 
                    submission.getTitle(), currentState);

        // Validate that reviewer is assigned
        if (submission.getReviewerEmail() == null || submission.getReviewerEmail().trim().isEmpty()) {
            logger.warn("No reviewer assigned to submission: {}", submission.getTitle());
            // This should be handled by the criterion, but we can log it here
        } else {
            // Validate that submitter and reviewer are different
            if (submission.getSubmitterEmail().equals(submission.getReviewerEmail())) {
                logger.warn("Submitter and reviewer cannot be the same for submission: {}", submission.getTitle());
                // This should be handled by the criterion, but we can log it here
            } else {
                logger.info("Reviewer {} assigned to submission {} by submitter {}", 
                           submission.getReviewerEmail(), submission.getTitle(), submission.getSubmitterEmail());
            }
        }

        return entityWithMetadata;
    }
}
