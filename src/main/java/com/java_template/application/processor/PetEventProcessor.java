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

import java.time.LocalDateTime;

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

        return serializer.withRequest(request)
            .toEntity(PetEvent.class)
            .validate(PetEvent::isValid)
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

        logger.info("PetEvent {} of type {} processed at {}", petEvent.getEventId(), petEvent.getEventType(), petEvent.getTimestamp());

        petEvent.setStatus(PetEvent.StatusEnum.PROCESSED);
        return petEvent;
    }
}
