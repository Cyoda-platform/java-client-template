package com.java_template.application.processor;

import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.RequestStatusEnum;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
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
            logger.info("Processing AdoptionRequest with ID: {}", request.getId());

            // Retrieve Pet by id
            Condition petCond = Condition.of("$.id", "EQUALS", request.getPetId());
            SearchConditionRequest petCondition = SearchConditionRequest.group("AND", petCond);
            CompletableFuture<ArrayNode> petItemsFuture = entityService.getItemsByCondition("Pet", Integer.parseInt(Config.ENTITY_VERSION), petCondition, true);
            ArrayNode petItems = petItemsFuture.get();

            if (petItems.isEmpty()) {
                logger.error("Pet with ID {} not found for adoption request", request.getPetId());
                request.setStatus(RequestStatusEnum.REJECTED);
                entityService.addItem("AdoptionRequest", Integer.parseInt(Config.ENTITY_VERSION), request).get();
                return request;
            }

            ObjectNode petNode = (ObjectNode) petItems.get(0);
            Pet pet = serializer.entityToJsonNode(request).traverse().readValueAs(Pet.class);

            if (pet.getStatus() != null && pet.getStatus() != Pet.PetStatusEnum.AVAILABLE) {
                logger.error("Pet with ID {} is not available for adoption in request", pet.getPetId());
                request.setStatus(RequestStatusEnum.REJECTED);
                entityService.addItem("AdoptionRequest", Integer.parseInt(Config.ENTITY_VERSION), request).get();
                return request;
            }

            // For simplicity approve all valid requests
            request.setStatus(RequestStatusEnum.APPROVED);
            entityService.addItem("AdoptionRequest", Integer.parseInt(Config.ENTITY_VERSION), request).get();

            logger.info("AdoptionRequest {} approved", request.getId());

            // Notification logic could be added here
            return request;
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error processing AdoptionRequest", e);
            request.setStatus(RequestStatusEnum.REJECTED);
            try {
                entityService.addItem("AdoptionRequest", Integer.parseInt(Config.ENTITY_VERSION), request).get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.error("Error updating AdoptionRequest status after failure", ex);
            }
            return request;
        }
    }
}
