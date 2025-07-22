package com.java_template.application.processor;

import com.java_template.application.entity.PetEvent;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PetEventStartProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetEventStartProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetEventStartProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetEvent for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(PetEvent.class)
            .validate(this::isValidEntity, "Invalid PetEvent state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetEventStartProcessor".equals(modelSpec.operationName()) &&
               "petevent".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetEvent petEvent) {
        return petEvent.getEventId() != null && !petEvent.getEventId().isBlank() &&
               petEvent.getPetId() != null && !petEvent.getPetId().isBlank() &&
               petEvent.getEventType() != null && !petEvent.getEventType().isBlank() &&
               petEvent.getEventTimestamp() != null &&
               petEvent.getStatus() != null && !petEvent.getStatus().isBlank();
    }

    private PetEvent processEntityLogic(PetEvent petEvent) {
        // Business logic based on functional requirements from prototype
        // Update PetEvent status to PROCESSED
        petEvent.setStatus("PROCESSED");
        return petEvent;
    }
}
