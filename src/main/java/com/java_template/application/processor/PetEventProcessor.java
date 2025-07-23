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
public class PetEventProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetEventProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetEventProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetEvent for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(PetEvent.class)
            .validate(PetEvent::isValid, "Invalid entity state")
            .map(this::processPetEvent)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetEventProcessor".equals(modelSpec.operationName()) &&
               "petEvent".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetEvent processPetEvent(PetEvent petEvent) {
        logger.info("Processing PetEvent with ID: {}", petEvent.getId());

        if (!petEvent.isValid()) {
            logger.error("PetEvent validation failed for ID: {}", petEvent.getId());
            return petEvent;
        }

        logger.info("Event {} for Pet {} of type {} recorded at {}", petEvent.getId(), petEvent.getPetId(), petEvent.getEventType(), petEvent.getTimestamp());

        return petEvent;
    }

}