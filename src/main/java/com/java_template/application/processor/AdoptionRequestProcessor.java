package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class AdoptionRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public AdoptionRequestProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("AdoptionRequestProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(AdoptionRequest::isValid, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AdoptionRequestProcessor".equals(modelSpec.operationName()) &&
               "adoptionRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private AdoptionRequest processEntityLogic(AdoptionRequest entity) {
        try {
            logger.info("Processing AdoptionRequest with ID: {}", entity.getRequestId());

            Condition condPetId = Condition.of("$.petId", "EQUALS", entity.getPetId());
            SearchConditionRequest condition = SearchConditionRequest.group("AND", condPetId);

            CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition("Pet", Config.ENTITY_VERSION, condition);
            ArrayNode pets = petsFuture.get();
            if (pets.isEmpty()) {
                entity.setStatus("REJECTED");
                updateAdoptionRequestStatus(entity);
                logger.info("AdoptionRequest rejected due to pet unavailability for ID: {}", entity.getRequestId());
                return entity;
            }
            ObjectNode petObj = (ObjectNode) pets.get(0);
            String status = petObj.get("status").asText();
            if (!"NEW".equals(status) && !"AVAILABLE".equals(status)) {
                entity.setStatus("REJECTED");
                updateAdoptionRequestStatus(entity);
                logger.info("AdoptionRequest rejected due to pet status for ID: {}", entity.getRequestId());
                return entity;
            }
            entity.setStatus("APPROVED");
            updateAdoptionRequestStatus(entity);
            logger.info("AdoptionRequest approved for ID: {}", entity.getRequestId());

            // Optional: notify requester
        } catch (Exception e) {
            logger.error("Error processing AdoptionRequest", e);
        }
        return entity;
    }

    private void updateAdoptionRequestStatus(AdoptionRequest entity) throws Exception {
        entityService.updateItem("AdoptionRequest", Config.ENTITY_VERSION, entity.getTechnicalId(), entity).get();
    }
}
