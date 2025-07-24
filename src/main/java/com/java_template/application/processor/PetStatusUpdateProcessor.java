package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetStatusUpdate;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.java_template.common.service.EntityService;

@Component
public class PetStatusUpdateProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetStatusUpdateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetStatusUpdateProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetStatusUpdate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetStatusUpdate.class)
            .validate(this::isValidEntity, "Invalid PetStatusUpdate state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetStatusUpdateProcessor".equals(modelSpec.operationName()) &&
               "petstatusupdate".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetStatusUpdate update) {
        return update != null && update.getId() != null && !update.getId().isBlank() && update.getPetId() != null && !update.getPetId().isBlank() && update.getNewStatus() != null && !update.getNewStatus().isBlank() && update.getStatus() != null && !update.getStatus().isBlank();
    }

    private PetStatusUpdate processEntityLogic(PetStatusUpdate update) {
        try {
            logger.info("Processing PetStatusUpdate with technicalId: {}", update.getTechnicalId());

            UUID petUuid = UUID.fromString(update.getPetId());
            CompletableFuture<ObjectNode> petFuture = entityService.getItem("Pet", Config.ENTITY_VERSION, petUuid);
            ObjectNode petNode = petFuture.get();
            if (petNode == null || petNode.isEmpty()) {
                logger.error("PetStatusUpdate processing failed: Pet {} not found", update.getPetId());
                update.setStatus("FAILED");
                entityService.updateItem("PetStatusUpdate", Config.ENTITY_VERSION, update.getTechnicalId(), update).get();
                return update;
            }

            // Create new Pet entity representing status update (immutable)
            Pet updatedPet = new Pet();
            updatedPet.setName(petNode.get("name").asText());
            updatedPet.setCategory(petNode.get("category").asText());
            List<String> photoUrls = new ArrayList<>();
            if (petNode.has("photoUrls") && petNode.get("photoUrls").isArray()) {
                petNode.get("photoUrls").forEach(n -> photoUrls.add(n.asText()));
            }
            updatedPet.setPhotoUrls(photoUrls);

            List<String> tags = new ArrayList<>();
            if (petNode.has("tags") && petNode.get("tags").isArray()) {
                petNode.get("tags").forEach(n -> tags.add(n.asText()));
            }
            updatedPet.setTags(tags);

            updatedPet.setStatus(update.getNewStatus());

            CompletableFuture<UUID> newPetIdFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, updatedPet);
            UUID newPetTechnicalId = newPetIdFuture.get();
            updatedPet.setTechnicalId(newPetTechnicalId);

            update.setStatus("PROCESSED");
            entityService.updateItem("PetStatusUpdate", Config.ENTITY_VERSION, update.getTechnicalId(), update).get();

            logger.info("PetStatusUpdate {} processed: Pet {} status updated to {}", update.getTechnicalId(), newPetTechnicalId, update.getNewStatus());

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error processing PetStatusUpdate: ", e);
            update.setStatus("FAILED");
            try {
                entityService.updateItem("PetStatusUpdate", Config.ENTITY_VERSION, update.getTechnicalId(), update).get();
            } catch (Exception ex) {
                logger.error("Failed to update PetStatusUpdate status to FAILED: ", ex);
            }
        }
        return update;
    }

}
