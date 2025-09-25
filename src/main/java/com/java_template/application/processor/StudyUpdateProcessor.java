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

import java.time.LocalDateTime;

/**
 * Processor for updating active studies
 */
@Component
public class StudyUpdateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StudyUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StudyUpdateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Study.class)
                .validate(this::isValidEntityWithMetadata, "Invalid study entity wrapper")
                .map(this::processStudyUpdate)
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

    private EntityWithMetadata<Study> processStudyUpdate(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Study> context) {

        EntityWithMetadata<Study> entityWithMetadata = context.entityResponse();
        Study study = entityWithMetadata.entity();

        logger.debug("Processing study update for study: {}", study.getStudyId());

        // Process study update
        updateStudy(study);
        
        // Update timestamps
        study.setUpdatedAt(LocalDateTime.now());

        logger.info("Study update processed for study {}", study.getStudyId());
        return entityWithMetadata;
    }

    private void updateStudy(Study study) {
        // Update enrollment metrics if needed
        if (study.getCurrentEnrollment() != null && study.getTargetEnrollment() != null) {
            double enrollmentRate = (double) study.getCurrentEnrollment() / study.getTargetEnrollment() * 100;
            logger.info("Study {} enrollment rate: {:.1f}%", study.getStudyId(), enrollmentRate);
        }
        
        // Log update
        logger.info("Study {} updated successfully", study.getStudyId());
    }
}
