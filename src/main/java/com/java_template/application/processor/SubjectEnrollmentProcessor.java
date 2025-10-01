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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class SubjectEnrollmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubjectEnrollmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubjectEnrollmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Subject.class)
                .validate(this::isValidEntityWithMetadata, "Invalid subject wrapper")
                .map(this::processSubjectEnrollment)
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

    private EntityWithMetadata<Subject> processSubjectEnrollment(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subject> context) {

        EntityWithMetadata<Subject> entityWithMetadata = context.entityResponse();
        Subject subject = entityWithMetadata.entity();

        logger.debug("Processing enrollment for subject: {}", subject.getSubjectId());

        subject.setUpdatedAt(LocalDateTime.now());
        logger.info("Subject {} enrollment processed", subject.getSubjectId());

        return entityWithMetadata;
    }
}
