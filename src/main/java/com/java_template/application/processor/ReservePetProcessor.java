package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ReservePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReservePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReservePetProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReservePet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid pet entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.getId() != null;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        try {
            if (pet == null) return null;
            // Attempt to load latest pet state from EntityService to perform optimistic reservation
            Pet remotePet = null;
            try {
                var node = entityService.getItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), UUID.fromString(pet.getId())).join();
                if (node != null && !node.isNull()) {
                    remotePet = objectMapper.convertValue(node, Pet.class);
                }
            } catch (Exception e) {
                logger.warn("Unable to load remote pet {} for reservation: {}", pet.getId(), e.getMessage());
            }

            Pet targetPet = remotePet != null ? remotePet : pet;
            if ("AVAILABLE".equalsIgnoreCase(targetPet.getStatus())) {
                targetPet.setStatus("RESERVED");
                List<String> tags = targetPet.getTags() == null ? new ArrayList<>() : new ArrayList<>(targetPet.getTags());
                tags.removeIf(t -> t != null && t.startsWith("reserved_by:"));
                tags.removeIf(t -> t != null && t.startsWith("reserved_until:"));
                tags.add("reserved_by:unknown_request");
                tags.add("reserved_until:" + Instant.now().plusSeconds(60 * 60).toString());
                targetPet.setTags(tags);
                try {
                    entityService.updateItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), UUID.fromString(targetPet.getId()), targetPet).join();
                    logger.info("Pet {} reserved (best-effort)", targetPet.getId());
                    return targetPet;
                } catch (Exception ex) {
                    logger.warn("Failed to persist reservation for pet {}: {}", targetPet.getId(), ex.getMessage());
                }
            } else {
                logger.info("Pet {} not available for reservation (status={})", targetPet.getId(), targetPet.getStatus());
            }
        } catch (Exception e) {
            logger.error("Error during ReservePetProcessor for pet {}: {}", pet == null ? "<null>" : pet.getId(), e.getMessage(), e);
        }
        return pet;
    }
}
