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
 * Processor for withdrawing subjects
 */
@Component
public class SubjectWithdrawalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubjectWithdrawalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubjectWithdrawalProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Subject.class)
                .validate(this::isValidEntityWithMetadata, "Invalid subject entity wrapper")
                .map(this::processSubjectWithdrawal)
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

    private EntityWithMetadata<Subject> processSubjectWithdrawal(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subject> context) {

        EntityWithMetadata<Subject> entityWithMetadata = context.entityResponse();
        Subject subject = entityWithMetadata.entity();

        logger.debug("Processing subject withdrawal for subject: {}", subject.getSubjectId());

        // Process subject withdrawal
        withdrawSubject(subject);
        
        // Update timestamps
        subject.setUpdatedAt(LocalDateTime.now());

        logger.info("Subject withdrawal processed for subject {}", subject.getSubjectId());
        return entityWithMetadata;
    }

    private void withdrawSubject(Subject subject) {
        // Update consent status
        subject.setConsentStatus("withdrawn");
        
        // Log withdrawal
        logger.info("Subject {} withdrawn from study {}", subject.getSubjectId(), subject.getStudyId());
    }
}
