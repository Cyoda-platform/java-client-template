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

import java.time.LocalDateTime;

/**
 * SubmissionCreationProcessor - Handles submission creation logic
 * Processes new submissions and sets initial values
 */
@Component
public class SubmissionCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubmissionCreationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Submission creation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Submission.class)
                .validate(this::isValidEntityWithMetadata, "Invalid submission entity wrapper")
                .map(this::processSubmissionCreationLogic)
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
     * Main business logic for submission creation
     */
    private EntityWithMetadata<Submission> processSubmissionCreationLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Submission> context) {

        EntityWithMetadata<Submission> entityWithMetadata = context.entityResponse();
        Submission submission = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing submission creation: {} in state: {}", submission.getTitle(), currentState);

        // Set submission timestamp if not already set
        if (submission.getSubmissionDate() == null) {
            submission.setSubmissionDate(LocalDateTime.now());
        }

        // Set default priority if not specified
        if (submission.getPriority() == null || submission.getPriority().trim().isEmpty()) {
            submission.setPriority("MEDIUM"); // Default priority
        }

        // Set default target decision date if not specified (30 days from now)
        if (submission.getTargetDecisionDate() == null) {
            submission.setTargetDecisionDate(LocalDateTime.now().plusDays(30));
        }

        // Validate target decision date is in the future
        if (submission.getTargetDecisionDate().isBefore(LocalDateTime.now())) {
            submission.setTargetDecisionDate(LocalDateTime.now().plusDays(30));
        }

        logger.info("Submission {} created successfully with type: {} and priority: {}", 
                   submission.getTitle(), submission.getSubmissionType(), submission.getPriority());

        return entityWithMetadata;
    }
}
