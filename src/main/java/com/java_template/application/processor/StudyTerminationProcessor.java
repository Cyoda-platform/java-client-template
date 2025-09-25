package com.java_template.application.processor;

import com.java_template.application.entity.study.version_1.Study;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Processor for terminating studies
 */
@Component
public class StudyTerminationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StudyTerminationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StudyTerminationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Study.class)
                .validate(this::isValidEntityWithMetadata, "Invalid study entity wrapper")
                .map(this::processStudyTermination)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Study> entityWithMetadata) {
        Study entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Study> processStudyTermination(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Study> context) {

        EntityWithMetadata<Study> entityWithMetadata = context.entityResponse();
        Study study = entityWithMetadata.entity();

        logger.debug("Processing study termination for study: {}", study.getStudyId());

        // Process study termination
        terminateStudy(study);
        
        // Update timestamps
        study.setUpdatedAt(LocalDateTime.now());

        logger.info("Study termination processed for study {}", study.getStudyId());
        return entityWithMetadata;
    }

    private void terminateStudy(Study study) {
        // Set actual end date
        if (study.getActualEndDate() == null) {
            study.setActualEndDate(LocalDate.now());
        }
        
        // Log termination
        logger.info("Study {} terminated", study.getStudyId());
        
        // Log final enrollment numbers
        if (study.getCurrentEnrollment() != null) {
            logger.info("Study {} enrollment at termination: {}", study.getStudyId(), study.getCurrentEnrollment());
        }
    }
}
