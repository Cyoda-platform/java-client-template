package com.java_template.application.processor;

import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.RequestStatusEnum;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class AdoptionRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AdoptionRequestProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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

    private AdoptionRequest processEntityLogic(AdoptionRequest request) {
        try {
            processAdoptionRequest(request);
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Exception during processing AdoptionRequest with ID: {}", request.getId(), e);
            request.setStatus(RequestStatusEnum.REJECTED);
            try {
                entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).get();
            } catch (Exception ex) {
                logger.error("Failed to persist rejected AdoptionRequest with ID: {}", request.getId(), ex);
            }
        }
        return request;
    }

    private void processAdoptionRequest(AdoptionRequest request) throws ExecutionException, InterruptedException {
        logger.info("Processing AdoptionRequest with ID: {}", request.getId());

        Condition petCond = Condition.of("$.id", "EQUALS", request.getPetId());
        SearchConditionRequest petCondition = SearchConditionRequest.group("AND", petCond);
        CompletableFuture<ArrayNode> petItemsFuture = entityService.getItemsByCondition("Pet", Config.ENTITY_VERSION, petCondition, true);
        ArrayNode petItems = petItemsFuture.get();

        if (petItems.isEmpty()) {
            logger.error("Pet with ID {} not found for adoption request", request.getPetId());
            request.setStatus(RequestStatusEnum.REJECTED);
            entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).get();
            return;
        }

        ObjectNode petNode = (ObjectNode) petItems.get(0);
        Pet pet = convertObjectNodeToPet(petNode);

        if (pet.getStatus() != Pet.PetStatusEnum.AVAILABLE) {
            logger.error("Pet with ID {} is not available for adoption in request", pet.getPetId());
            request.setStatus(RequestStatusEnum.REJECTED);
            entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).get();
            return;
        }

        request.setStatus(RequestStatusEnum.APPROVED);
        entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).get();

        logger.info("AdoptionRequest {} approved", request.getId());
    }

    private Pet convertObjectNodeToPet(ObjectNode petNode) {
        Pet pet = new Pet();
        if (petNode.has("petId")) pet.setPetId(petNode.get("petId").asText());
        if (petNode.has("name")) pet.setName(petNode.get("name").asText());
        if (petNode.has("category")) pet.setCategory(petNode.get("category").asText());
        if (petNode.has("status")) {
            try {
                pet.setStatus(Pet.PetStatusEnum.valueOf(petNode.get("status").asText()));
            } catch (IllegalArgumentException e) {
                logger.error("Invalid PetStatusEnum value: {}", petNode.get("status").asText());
            }
        }
        return pet;
    }
}
