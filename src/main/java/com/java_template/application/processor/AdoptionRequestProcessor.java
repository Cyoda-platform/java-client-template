package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();
    private final com.java_template.common.service.EntityService entityService;

    public AdoptionRequestProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest adoptionRequest = context.entity();
        String technicalId = context.request().getEntityId();

        logger.info("Processing AdoptionRequest with id: {}", technicalId);

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", adoptionRequest.getPetId())
            );

            CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    com.java_template.common.config.Config.ENTITY_VERSION,
                    condition,
                    true
            );

            ArrayNode petNodes = petsFuture.get();
            if (petNodes == null || petNodes.isEmpty()) {
                logger.error("Pet {} referenced in AdoptionRequest {} not found", adoptionRequest.getPetId(), technicalId);
                adoptionRequest.setStatus("REJECTED");
                return adoptionRequest;
            }

            ObjectNode petNode = (ObjectNode) petNodes.get(0);
            Pet pet = Pet.fromJsonNode(petNode);

            if (!"available".equalsIgnoreCase(pet.getStatus())) {
                logger.error("Pet {} is not available for AdoptionRequest {}", adoptionRequest.getPetId(), technicalId);
                adoptionRequest.setStatus("REJECTED");
                return adoptionRequest;
            }

            Pet newPetVersion = new Pet();
            newPetVersion.setName(pet.getName());
            newPetVersion.setCategory(pet.getCategory());
            newPetVersion.setPhotoUrls(pet.getPhotoUrls());
            newPetVersion.setTags(pet.getTags());
            newPetVersion.setCreatedAt(pet.getCreatedAt());
            newPetVersion.setStatus("pending");

            CompletableFuture<UUID> newPetIdFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    com.java_template.common.config.Config.ENTITY_VERSION,
                    newPetVersion
            );
            UUID newPetTechnicalId = newPetIdFuture.get();
            logger.info("Created new Pet version {} with status 'pending' due to AdoptionRequest {}", newPetTechnicalId, technicalId);

            adoptionRequest.setStatus("APPROVED");
            logger.info("AdoptionRequest {} APPROVED", technicalId);

        } catch (Exception e) {
            logger.error("Exception in processAdoptionRequest", e);
            adoptionRequest.setStatus("REJECTED");
        }

        return adoptionRequest;
    }
}
