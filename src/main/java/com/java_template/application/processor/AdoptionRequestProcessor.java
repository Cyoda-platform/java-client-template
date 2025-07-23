package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Pet;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
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
                .map(this::processAdoptionRequestLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AdoptionRequestProcessor".equals(modelSpec.operationName()) &&
               "adoptionRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private AdoptionRequest processAdoptionRequestLogic(AdoptionRequest request) {
        logger.info("Processing AdoptionRequest with technicalId: {}", request.getTechnicalId());

        // Fetch Pet entity data as ObjectNode
        CompletableFuture<ObjectNode> petFuture = entityService.getItem("pet", request.getPetId(), ObjectNode.class);
        ObjectNode petObjectNode;
        UUID petTechId = null;
        try {
            petObjectNode = petFuture.get();
            if (petObjectNode != null) {
                petTechId = UUID.fromString(petObjectNode.path("technicalId").asText(null));
            }
        } catch (Exception e) {
            logger.error("Failed to fetch Pet for AdoptionRequest {}: {}", request.getTechnicalId(), e.getMessage());
            petObjectNode = null;
        }

        if (petObjectNode == null || petObjectNode.isEmpty()) {
            logger.error("AdoptionRequest {} references unknown Pet technicalId: {}", request.getTechnicalId(), request.getPetId());
            request.setStatus("REJECTED");
            entityService.updateItem("adoptionRequest", Config.ENTITY_VERSION, request.getTechnicalId(), request);
            return request;
        }

        String petStatus = petObjectNode.path("status").asText(null);
        if (!"AVAILABLE".equalsIgnoreCase(petStatus)) {
            logger.error("AdoptionRequest {} rejected because Pet {} status is {}", request.getTechnicalId(), request.getPetId(), petStatus);
            request.setStatus("REJECTED");
            entityService.updateItem("adoptionRequest", Config.ENTITY_VERSION, request.getTechnicalId(), request);
            return request;
        }

        if (request.getRequesterName() != null && !request.getRequesterName().isBlank()) {
            request.setStatus("APPROVED");
            // Update pet status to ADOPTED
            Pet pet = new Pet();
            pet.setTechnicalId(petTechId);
            pet.setStatus("ADOPTED");
            entityService.updateItem("pet", Config.ENTITY_VERSION, petTechId, pet)
                    .thenAccept(updatedId -> logger.info("Pet {} adopted", petTechId))
                    .exceptionally(e -> {
                        logger.error("Failed to update Pet {} to ADOPTED: {}", petTechId, e.getMessage());
                        return null;
                    });
            logger.info("AdoptionRequest {} approved, Pet {} adopted", request.getTechnicalId(), petTechId);
        } else {
            request.setStatus("REJECTED");
            logger.info("AdoptionRequest {} rejected due to invalid requester name", request.getTechnicalId());
        }
        entityService.updateItem("adoptionRequest", Config.ENTITY_VERSION, request.getTechnicalId(), request)
                .exceptionally(e -> {
                    logger.error("Failed to update AdoptionRequest {} status: {}", request.getTechnicalId(), e.getMessage());
                    return null;
                });

        return request;
    }
}
