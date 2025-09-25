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
 * Processor for intake review of submissions
 */
@Component
public class IntakeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IntakeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IntakeProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Submission.class)
                .validate(this::isValidEntityWithMetadata, "Invalid submission entity wrapper")
                .map(this::processIntakeReview)
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

    private EntityWithMetadata<Submission> processIntakeReview(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Submission> context) {

        EntityWithMetadata<Submission> entityWithMetadata = context.entityResponse();
        Submission submission = entityWithMetadata.entity();

        logger.debug("Processing intake review for submission: {}", submission.getSubmissionId());

        // Perform intake completeness check
        performCompletenessCheck(submission);
        
        // Update timestamps
        submission.setUpdatedAt(LocalDateTime.now());

        logger.info("Intake review completed for submission {}", submission.getSubmissionId());
        return entityWithMetadata;
    }

    private void performCompletenessCheck(Submission submission) {
        // Check if all required documents are provided
        if (submission.getAttachmentsRequired() != null && submission.getAttachmentsProvided() != null) {
            for (String requiredDoc : submission.getAttachmentsRequired()) {
                if (!submission.getAttachmentsProvided().contains(requiredDoc)) {
                    logger.warn("Missing required document: {} for submission {}", requiredDoc, submission.getSubmissionId());
                }
            }
        }
        
        // Log intake completion
        logger.info("Completeness check completed for submission: {}", submission.getSubmissionId());
    }
}
