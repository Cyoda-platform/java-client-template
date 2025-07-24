package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("PetProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Pet.class)
                .validate(Pet::isValid, "Invalid pet data")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
                "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Pet processEntityLogic(Pet pet) {
        try {
            if (pet.getPetId() == null) {
                logger.error("Pet id is null, cannot process");
                return pet;
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "pet",
                    Integer.parseInt(Config.ENTITY_VERSION),
                    UUID.nameUUIDFromBytes(pet.getPetId().toString().getBytes())
            );
            ObjectNode petNode = itemFuture.join();
            if (petNode == null || petNode.isEmpty()) {
                logger.error("Pet not found during processing with ID: {}", pet.getPetId());
                return pet;
            }
            Pet fetchedPet = objectMapper.treeToValue(petNode, Pet.class);
            if (!fetchedPet.isValid()) {
                logger.error("Invalid fetched pet data for ID: {}", pet.getPetId());
                return pet;
            }
            if (fetchedPet.getTags() == null) {
                fetchedPet.setTags(new ArrayList<>());
            }
            if (!fetchedPet.getTags().contains("Purrfect")) {
                fetchedPet.getTags().add("Purrfect");
            }
            entityService.addItem(
                    "pet",
                    Integer.parseInt(Config.ENTITY_VERSION),
                    fetchedPet
            ).join();
            logger.info("Processed Pet with petId: {}", pet.getPetId());
            return fetchedPet;
        } catch (Exception e) {
            logger.error("Error processing pet with ID {}: {}", pet.getPetId(), e.getMessage());
            return pet;
        }
    }
}
