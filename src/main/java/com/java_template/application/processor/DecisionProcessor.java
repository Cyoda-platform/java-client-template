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
 * DecisionProcessor - Handles submission decision logic
 * Processes approval/rejection decisions for submissions
 */
@Component
public class DecisionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DecisionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DecisionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Decision for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Submission.class)
                .validate(this::isValidEntityWithMetadata, "Invalid submission entity wrapper")
                .map(this::processDecisionLogic)
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
     * Main business logic for decision processing
     */
    private EntityWithMetadata<Submission> processDecisionLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Submission> context) {

        EntityWithMetadata<Submission> entityWithMetadata = context.entityResponse();
        Submission submission = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing decision for submission: {} in state: {}", 
                    submission.getTitle(), currentState);

        // Validate that a reviewer is assigned
        if (submission.getReviewerEmail() == null || submission.getReviewerEmail().trim().isEmpty()) {
            logger.warn("No reviewer assigned for decision on submission: {}", submission.getTitle());
        }

        // Log the decision being made
        if ("approved".equals(currentState)) {
            logger.info("Submission {} approved by reviewer {}", 
                       submission.getTitle(), submission.getReviewerEmail());
        } else if ("rejected".equals(currentState)) {
            logger.info("Submission {} rejected by reviewer {}", 
                       submission.getTitle(), submission.getReviewerEmail());
            
            // Ensure decision reason is provided for rejections
            if (submission.getDecisionReason() == null || submission.getDecisionReason().trim().isEmpty()) {
                submission.setDecisionReason("No reason provided");
                logger.warn("No decision reason provided for rejected submission: {}", submission.getTitle());
            }
        }

        return entityWithMetadata;
    }
}
