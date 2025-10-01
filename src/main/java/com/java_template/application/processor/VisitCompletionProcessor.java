package com.java_template.application.processor;

import com.java_template.application.entity.visit.version_1.Visit;
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
public class VisitCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VisitCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public VisitCompletionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Visit.class)
                .validate(this::isValidEntityWithMetadata, "Invalid visit wrapper")
                .map(this::processVisitCompletion)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Visit> entityWithMetadata) {
        Visit entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Visit> processVisitCompletion(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Visit> context) {

        EntityWithMetadata<Visit> entityWithMetadata = context.entityResponse();
        Visit visit = entityWithMetadata.entity();

        logger.debug("Processing completion for visit: {}", visit.getVisitId());

        visit.setUpdatedAt(LocalDateTime.now());
        logger.info("Visit {} completion processed", visit.getVisitId());

        return entityWithMetadata;
    }
}
