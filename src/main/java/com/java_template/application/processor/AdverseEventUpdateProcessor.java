package com.java_template.application.processor;

import com.java_template.application.entity.adverse_event.version_1.AdverseEvent;
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
public class AdverseEventUpdateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdverseEventUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AdverseEventUpdateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(AdverseEvent.class)
                .validate(this::isValidEntityWithMetadata, "Invalid adverse event wrapper")
                .map(this::processAdverseEventUpdate)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<AdverseEvent> entityWithMetadata) {
        AdverseEvent entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<AdverseEvent> processAdverseEventUpdate(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<AdverseEvent> context) {

        EntityWithMetadata<AdverseEvent> entityWithMetadata = context.entityResponse();
        AdverseEvent adverseEvent = entityWithMetadata.entity();

        logger.debug("Processing update for adverse event: {}", adverseEvent.getAdverseEventId());

        adverseEvent.setUpdatedAt(LocalDateTime.now());
        logger.info("Adverse event {} update processed", adverseEvent.getAdverseEventId());

        return entityWithMetadata;
    }
}
