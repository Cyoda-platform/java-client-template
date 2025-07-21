package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Pet;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class AdoptionRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdoptionRequestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("AdoptionRequestProcessor initialized with SerializerFactory, EntityService, and ObjectMapper");
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

    private AdoptionRequest processAdoptionRequestLogic(AdoptionRequest adoptionRequest) {
        try {
            logger.info("Processing AdoptionRequest with technicalId: {}", adoptionRequest.getTechnicalId());

            UUID petTechnicalId = UUID.fromString(adoptionRequest.getPetId().toString());
            ObjectNode petNode = entityService.getItem("Pet", Config.ENTITY_VERSION, petTechnicalId).get();

            if (petNode == null || petNode.isEmpty()) {
                logger.error("AdoptionRequest processing failed: Pet not found with technicalId: {}", adoptionRequest.getPetId());
                return adoptionRequest;
            }

            Pet pet = objectMapper.convertValue(petNode, Pet.class);

            if (pet.getStatus() != Pet.StatusEnum.AVAILABLE) {
                logger.error("AdoptionRequest processing failed: Pet with technicalId {} not available", petTechnicalId);
                return adoptionRequest;
            }

            Pet updatedPet = new Pet();
            updatedPet.setTechnicalId(pet.getTechnicalId());
            updatedPet.setId(pet.getId());
            updatedPet.setName(pet.getName());
            updatedPet.setCategory(pet.getCategory());
            updatedPet.setPhotoUrls(pet.getPhotoUrls());
            updatedPet.setTags(pet.getTags());
            updatedPet.setStatus(Pet.StatusEnum.PENDING_ADOPTION);

            entityService.addItem("Pet", Config.ENTITY_VERSION, updatedPet).get();

            logger.info("AdoptionRequest {} processed: Pet {} status updated to PENDING_ADOPTION", adoptionRequest.getTechnicalId(), petTechnicalId);

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Exception during AdoptionRequest processing", e);
        }
        return adoptionRequest;
    }
}
