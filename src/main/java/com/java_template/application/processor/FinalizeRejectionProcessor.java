package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class FinalizeRejectionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FinalizeRejectionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FinalizeRejectionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FinalizeRejection for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid adoption request entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.getPetId() != null;
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest ar = context.entity();
        try {
            ar.setStatus("REJECTED");

            // If pet associated and reservation markers present, release reservation
            Pet pet = ar.getPet();
            if (pet == null && ar.getPetId() != null) {
                try {
                    JsonNode petNode = entityService.getItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), UUID.fromString(ar.getPetId())).join();
                    if (petNode != null && !petNode.isNull()) {
                        pet = objectMapper.convertValue(petNode, Pet.class);
                    }
                } catch (Exception e) {
                    logger.warn("Unable to load pet {} during finalize rejection: {}", ar.getPetId(), e.getMessage());
                }
            }

            if (pet != null) {
                List<String> tags = pet.getTags() == null ? new ArrayList<>() : new ArrayList<>(pet.getTags());
                boolean hadReservation = false;
                String reservedBy = null;
                for (String t : tags) {
                    if (t != null && t.startsWith("reserved_by:")) {
                        hadReservation = true;
                        reservedBy = t.substring("reserved_by:".length());
                        break;
                    }
                }
                if (hadReservation && reservedBy != null && ar.getId() != null && ar.getId().equals(reservedBy)) {
                    pet.setStatus("AVAILABLE");
                    List<String> newTags = new ArrayList<>();
                    for (String t : tags) {
                        if (t != null && (t.startsWith("reserved_by:") || t.startsWith("reserved_until:"))) continue;
                        newTags.add(t);
                    }
                    pet.setTags(newTags);
                    try {
                        entityService.updateItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), UUID.fromString(pet.getId()), pet).join();
                    } catch (Exception ex) {
                        logger.warn("Failed to persist pet {} during finalize rejection: {}", pet.getId(), ex.getMessage());
                    }
                }
            }

            logger.info("AdoptionRequest {} finalized as REJECTED", ar.getId());
        } catch (Exception e) {
            logger.error("Error during FinalizeRejectionProcessor for request {}: {}", ar == null ? "<null>" : ar.getId(), e.getMessage(), e);
        }
        return ar;
    }
}
