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
import com.java_template.common.service.EntityService;

@Component
public class PetEventProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetEventProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetEventProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetEvent for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetEvent.class)
            .validate(PetEvent::isValid, "Invalid PetEvent state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetEventProcessor".equals(modelSpec.operationName()) &&
               "petEvent".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetEvent processEntityLogic(PetEvent entity) {
        try {
            logger.info("Processing PetEvent with ID: {}", entity.getId());
            if (!entity.isValid()) {
                logger.error("Invalid PetEvent during processing: {}", entity);
                entity.setStatus("FAILED");
                entityService.addItem("PetEvent", Config.ENTITY_VERSION, entity).get();
                return entity;
            }
            // Notify owner or trigger workflows here (business logic)
            entity.setStatus("PROCESSED");
            entityService.addItem("PetEvent", Config.ENTITY_VERSION, entity).get();
            logger.info("PetEvent {} processed successfully", entity.getId());
        } catch (Exception e) {
            logger.error("Error processing PetEvent: {}", e.getMessage(), e);
            // Optionally handle errors
        }
        return entity;
    }
}
