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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

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
                .validate(this::isValidEntityWithMetadata, "Invalid study wrapper")
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

        logger.debug("Processing setup for study: {}", study.getStudyId());

        study.setUpdatedAt(LocalDateTime.now());
        logger.info("Study {} setup processed", study.getStudyId());

        return entityWithMetadata;
    }
}
