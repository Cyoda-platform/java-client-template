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
 * Processor for setting up study configuration
 */
@Component
public class StudySetupProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StudySetupProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StudySetupProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Study.class)
                .validate(this::isValidEntityWithMetadata, "Invalid study entity wrapper")
                .map(this::processStudySetup)
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

    private EntityWithMetadata<Study> processStudySetup(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Study> context) {

        EntityWithMetadata<Study> entityWithMetadata = context.entityResponse();
        Study study = entityWithMetadata.entity();

        logger.debug("Processing study setup for study: {}", study.getStudyId());

        // Process study setup
        setupStudy(study);
        
        // Update timestamps
        study.setUpdatedAt(LocalDateTime.now());

        logger.info("Study setup processed for study {}", study.getStudyId());
        return entityWithMetadata;
    }

    private void setupStudy(Study study) {
        // Validate study configuration
        if (study.getVisitSchedule() == null || study.getVisitSchedule().isEmpty()) {
            logger.warn("Study {} has no visit schedule defined", study.getStudyId());
        }
        
        if (study.getArms() == null || study.getArms().isEmpty()) {
            logger.warn("Study {} has no study arms defined", study.getStudyId());
        }
        
        if (study.getSites() == null || study.getSites().isEmpty()) {
            logger.warn("Study {} has no sites defined", study.getStudyId());
        }
        
        // Log setup completion
        logger.info("Study setup validated for study {}", study.getStudyId());
    }
}
