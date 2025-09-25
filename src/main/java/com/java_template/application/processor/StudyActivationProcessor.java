package com.java_template.application.processor;

import com.java_template.application.entity.submission.version_1.Submission;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
 * Processor for activating studies from approved submissions
 */
@Component
public class StudyActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StudyActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public StudyActivationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Submission.class)
                .validate(this::isValidEntityWithMetadata, "Invalid submission entity wrapper")
                .map(this::processStudyActivation)
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

    private EntityWithMetadata<Submission> processStudyActivation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Submission> context) {

        EntityWithMetadata<Submission> entityWithMetadata = context.entityResponse();
        Submission submission = entityWithMetadata.entity();

        logger.debug("Processing study activation for submission: {}", submission.getSubmissionId());

        // Activate study from approved submission
        activateStudy(submission);
        
        // Update timestamps
        submission.setUpdatedAt(LocalDateTime.now());

        logger.info("Study activation processed for submission {}", submission.getSubmissionId());
        return entityWithMetadata;
    }

    private void activateStudy(Submission submission) {
        // Log study activation
        logger.info("Activating study for submission {}", submission.getSubmissionId());
        
        // Create study shell from submission data
        logger.info("Creating study shell for protocol {}", submission.getProtocolId());
        
        // TODO: When Study entity is implemented, create Study entity here
        // For now, just log the activation
        logger.info("Study shell created for submission {} with protocol ID {}", 
                   submission.getSubmissionId(), submission.getProtocolId());
    }
}
