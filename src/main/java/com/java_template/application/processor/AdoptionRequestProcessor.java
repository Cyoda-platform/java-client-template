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
import com.java_template.common.service.EntityService;

import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
        logger.info("Processing AdoptionRequest entity logic");
        try {
            UUID petUuid = UUID.fromString(request.getPetId());
            CompletableFuture<ObjectNode> petNodeFuture = entityService.getItem("pet", Config.ENTITY_VERSION, petUuid);
            ObjectNode petNode = petNodeFuture.get();

            if (petNode == null || petNode.isEmpty()) {
                logger.error("Referenced pet does not exist for adoption request");
                request.setStatus("REJECTED");
                return request;
            }

            Pet pet = entityService.getObjectMapper().treeToValue(petNode, Pet.class);

            if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
                logger.info("Pet is not available for adoption, rejecting request");
                request.setStatus("REJECTED");
                return request;
            }

            request.setStatus("APPROVED");
            logger.info("AdoptionRequest approved for pet");

            Pet adoptedPet = new Pet();
            adoptedPet.setName(pet.getName());
            adoptedPet.setType(pet.getType());
            adoptedPet.setStatus("ADOPTED");
            adoptedPet.setCreatedAt(LocalDateTime.now());

            entityService.addItem("pet", Config.ENTITY_VERSION, adoptedPet).get();

            logger.info("Pet status updated to ADOPTED by creating new entity");
        } catch (Exception e) {
            logger.error("Failed processing adoption request: {}", e.getMessage());
            request.setStatus("REJECTED");
        }
        return request;
    }
}
