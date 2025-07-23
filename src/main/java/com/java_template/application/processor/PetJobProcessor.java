package com.java_template.application.processor;

import com.java_template.application.entity.PetJob;
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.java_template.common.service.EntityService;

@Component
public class PetJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetJob.class)
            .validate(PetJob::isValid, "Invalid PetJob entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
               "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetJob processEntityLogic(PetJob entity) {
        petJobProcessing(entity.getTechnicalId(), entity.getPetType());
        return entity;
    }

    private void petJobProcessing(UUID technicalId, String petType) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetJob", Config.ENTITY_VERSION, technicalId);
            ObjectNode petJobNode = itemFuture.get();
            if (petJobNode == null) {
                logger.error("PetJob not found during processing: {}", technicalId);
                return;
            }

            if (petType == null || petType.isBlank()) {
                logger.error("Invalid petType in PetJob: {}", petType);
                petJobNode.put("status", "FAILED");
                entityService.updateItem("PetJob", Config.ENTITY_VERSION, technicalId, petJobNode).get();
                return;
            }

            petJobNode.put("status", "PROCESSING");
            entityService.updateItem("PetJob", Config.ENTITY_VERSION, technicalId, petJobNode).get();

            com.java_template.application.entity.Pet dummyPet = new com.java_template.application.entity.Pet();
            dummyPet.setPetId(null);
            dummyPet.setName("DummyPet_" + UUID.randomUUID());
            dummyPet.setType(petType);
            dummyPet.setStatus("ACTIVE");

            CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, dummyPet);
            UUID petTechnicalId = petIdFuture.get();
            logger.info("PetJob processed: Created dummy Pet with technicalId {}", petTechnicalId);

            petJobNode.put("status", "COMPLETED");
            entityService.updateItem("PetJob", Config.ENTITY_VERSION, technicalId, petJobNode).get();

        } catch (Exception e) {
            logger.error("Error processing PetJob with technicalId {}: {}", technicalId, e.getMessage());
        }
    }

}