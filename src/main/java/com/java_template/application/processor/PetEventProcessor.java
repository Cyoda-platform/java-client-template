package com.java_template.application.processor;

import com.java_template.application.entity.PetEvent;
import com.java_template.application.entity.Pet;
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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;

import java.util.concurrent.CompletableFuture;

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
            .validate(PetEvent::isValid)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetEventProcessor".equals(modelSpec.operationName()) &&
               "petEvent".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetEvent processEntityLogic(PetEvent event) {
        logger.info("Processing PetEvent with ID: {}", event.getEventId());
        Pet relatedPet = null;
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.petId", "EQUALS", event.getPetId()));
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition("Pet", Integer.parseInt(Config.ENTITY_VERSION), condition);
            ArrayNode resultArray = future.join();
            if (resultArray != null && !resultArray.isEmpty()) {
                ObjectNode obj = (ObjectNode) resultArray.get(0);
                relatedPet = new Pet();
                relatedPet.setPetId(obj.get("petId").asText());
            }
        } catch (Exception e) {
            logger.error("Error retrieving related Pet for PetEvent {}: {}", event.getEventId(), e.getMessage());
        }
        if (relatedPet == null) {
            logger.error("PetEvent {} processing failed: related Pet {} not found", event.getEventId(), event.getPetId());
            event.setStatus("FAILED");
            return event;
        }
        event.setStatus("PROCESSED");
        logger.info("PetEvent {} processed successfully for Pet {}", event.getEventId(), event.getPetId());
        return event;
    }
}
