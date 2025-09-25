package com.java_template.application.processor;

import com.java_template.application.entity.subject.version_1.Subject;
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
 * Processor for completing subjects
 */
@Component
public class SubjectCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubjectCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubjectCompletionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Subject.class)
                .validate(this::isValidEntityWithMetadata, "Invalid subject entity wrapper")
                .map(this::processSubjectCompletion)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Subject> entityWithMetadata) {
        Subject entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Subject> processSubjectCompletion(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subject> context) {

        EntityWithMetadata<Subject> entityWithMetadata = context.entityResponse();
        Subject subject = entityWithMetadata.entity();

        logger.debug("Processing subject completion for subject: {}", subject.getSubjectId());

        // Process subject completion
        completeSubject(subject);
        
        // Update timestamps
        subject.setUpdatedAt(LocalDateTime.now());

        logger.info("Subject completion processed for subject {}", subject.getSubjectId());
        return entityWithMetadata;
    }

    private void completeSubject(Subject subject) {
        // Log completion
        logger.info("Subject {} completed in study {}", subject.getSubjectId(), subject.getStudyId());
    }
}
