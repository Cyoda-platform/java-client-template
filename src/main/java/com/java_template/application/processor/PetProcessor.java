package com.java_template.application.processor;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public PetProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Pet.class)
                .validate(Pet::isValid)
                .map(this::processPetLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
                "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Pet processPetLogic(Pet pet) {
        try {
            logger.info("Processing Pet with ID: {}", pet.getId());

            if (pet.getTags() == null || pet.getTags().isEmpty()) {
                pet.setTags(new ArrayList<>(Collections.singletonList("fun pet")));
                logger.info("Added default fun pet tag to Pet {}", pet.getId());
            }

            if ("NEW".equalsIgnoreCase(pet.getStatus())) {
                // Create new version with status AVAILABLE
                Pet updatedPet = new Pet();
                updatedPet.setName(pet.getName());
                updatedPet.setCategory(pet.getCategory());
                updatedPet.setPhotoUrls(pet.getPhotoUrls());
                updatedPet.setTags(pet.getTags());
                updatedPet.setStatus("AVAILABLE");

                CompletableFuture<UUID> updatedIdFuture = entityService.addItem(
                        "Pet",
                        Config.ENTITY_VERSION,
                        updatedPet
                );
                UUID updatedId = updatedIdFuture.get();
                String updatedIdStr = updatedId.toString();
                updatedPet.setId(updatedIdStr);
                updatedPet.setPetId(updatedIdStr);

                logger.info("Pet {} status updated to AVAILABLE", updatedPet.getId());
            }

            // Finalize pet entity state (no further action)
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error processing Pet entity", e);
            Thread.currentThread().interrupt();
        }

        return pet;
    }
}
