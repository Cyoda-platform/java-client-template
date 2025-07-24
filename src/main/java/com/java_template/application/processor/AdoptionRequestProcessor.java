package com.java_template.application.processor;

import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.ErrorInfo;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.util.concurrent.CompletableFuture;

@Component
public class AdoptionRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper mapper;

    public AdoptionRequestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper mapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.mapper = mapper;
        logger.info("AdoptionRequestProcessor initialized with SerializerFactory, EntityService, and ObjectMapper");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(AdoptionRequest.class)
                .validate(this::isValidEntity)
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AdoptionRequestProcessor".equals(modelSpec.operationName()) &&
                "adoptionRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity.isValid();
    }

    private AdoptionRequest processEntityLogic(AdoptionRequest request) {
        logger.info("Processing AdoptionRequest with technicalId: {}", request.getTechnicalId());

        Condition condition = Condition.of("$.petId", "EQUALS", request.getPetId());
        SearchConditionRequest searchRequest = SearchConditionRequest.group("AND", condition);
        CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition("Pet", Config.ENTITY_VERSION, searchRequest, true);
        ArrayNode petsArray = petsFuture.join();

        if (petsArray == null || petsArray.isEmpty()) {
            logger.error("Pet with petId {} not found for adoption request {}", request.getPetId(), request.getTechnicalId());
            request.setStatus("REJECTED");
            entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).join();
            return request;
        }

        Pet pet = null;
        try {
            ObjectNode petNode = (ObjectNode) petsArray.get(0);
            pet = mapper.treeToValue(petNode, Pet.class);
        } catch (Exception e) {
            logger.error("Error deserializing Pet for adoption request {}: {}", request.getTechnicalId(), e.getMessage());
            request.setStatus("REJECTED");
            entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).join();
            return request;
        }

        if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
            logger.warn("Pet {} is not available for adoption", pet.getTechnicalId());
            request.setStatus("REJECTED");
            entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).join();
            return request;
        }

        request.setStatus("APPROVED");
        entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).join();

        pet.setStatus("ADOPTED");
        entityService.addItem("Pet", Config.ENTITY_VERSION, pet).join();

        logger.info("AdoptionRequest {} approved and Pet {} marked as ADOPTED", request.getTechnicalId(), pet.getTechnicalId());

        return request;
    }
}
