package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.owner.version_1.Owner;
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
public class FinalizeAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FinalizeAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FinalizeAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FinalizeAdoption for request: {}", request.getId());

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
        return entity != null && entity.getPetId() != null && entity.getOwnerId() != null;
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest ar = context.entity();
        try {
            // Load pet and owner via EntityService if nested payload not provided
            Pet pet = ar.getPet();
            Owner owner = ar.getOwner();

            if (pet == null) {
                try {
                    JsonNode petNode = entityService.getItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), UUID.fromString(ar.getPetId())).join();
                    if (petNode != null && !petNode.isNull()) {
                        pet = objectMapper.convertValue(petNode, Pet.class);
                    }
                } catch (Exception e) {
                    logger.warn("Unable to load pet {} during finalize adoption: {}", ar.getPetId(), e.getMessage());
                }
            }

            if (owner == null) {
                try {
                    JsonNode ownerNode = entityService.getItem(Owner.ENTITY_NAME, String.valueOf(Owner.ENTITY_VERSION), UUID.fromString(ar.getOwnerId())).join();
                    if (ownerNode != null && !ownerNode.isNull()) {
                        owner = objectMapper.convertValue(ownerNode, Owner.class);
                    }
                } catch (Exception e) {
                    logger.warn("Unable to load owner {} during finalize adoption: {}", ar.getOwnerId(), e.getMessage());
                }
            }

            if (pet == null || owner == null) {
                logger.warn("Cannot finalize adoption {} because pet or owner could not be loaded", ar.getId());
                ar.setStatus("NEEDS_REVIEW");
                return ar;
            }

            // Check reservation markers in tags
            boolean reserved = false;
            String reservedBy = null;
            List<String> tags = pet.getTags() == null ? new ArrayList<>() : new ArrayList<>(pet.getTags());
            for (String t : tags) {
                if (t != null && t.startsWith("reserved_by:")) {
                    reserved = true;
                    reservedBy = t.substring("reserved_by:".length());
                    break;
                }
            }

            if (reserved && "RESERVED".equalsIgnoreCase(pet.getStatus()) && ar.getId() != null && ar.getId().equals(reservedBy)) {
                // finalize adoption: mark pet ADOPTED, remove reservation markers, persist pet and update owner
                pet.setStatus("ADOPTED");
                List<String> newTags = new ArrayList<>();
                for (String t : tags) {
                    if (t != null && (t.startsWith("reserved_by:") || t.startsWith("reserved_until:"))) continue;
                    newTags.add(t);
                }
                pet.setTags(newTags);

                try {
                    entityService.updateItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), UUID.fromString(pet.getId()), pet).join();
                } catch (Exception ex) {
                    logger.warn("Failed to persist pet {} during finalize adoption: {}", pet.getId(), ex.getMessage());
                    // continue, adoption will still be recorded in ar
                }

                if (owner.getAdoptionHistory() == null) owner.setAdoptionHistory(new ArrayList<>());
                owner.getAdoptionHistory().add(ar.getId() + "@" + Instant.now().toString());

                try {
                    entityService.updateItem(Owner.ENTITY_NAME, String.valueOf(Owner.ENTITY_VERSION), UUID.fromString(owner.getId()), owner).join();
                } catch (Exception ex) {
                    logger.warn("Failed to persist owner {} during finalize adoption: {}", owner.getId(), ex.getMessage());
                }

                ar.setStatus("APPROVED");
                ar.setProcessedBy("system");

                logger.info("Adoption {} finalized: pet {} adopted by owner {}", ar.getId(), pet.getId(), owner.getId());
            } else {
                logger.warn("Cannot finalize adoption {} due to reservation mismatch or pet not reserved", ar.getId());
                ar.setStatus("NEEDS_REVIEW");
            }

        } catch (Exception e) {
            logger.error("Error during FinalizeAdoptionProcessor for request {}: {}", ar == null ? "<null>" : ar.getId(), e.getMessage(), e);
        }
        return ar;
    }
}
