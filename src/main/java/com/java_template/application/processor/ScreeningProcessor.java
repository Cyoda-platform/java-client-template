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
public class ScreeningProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScreeningProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ScreeningProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Screening for request: {}", request.getId());

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
            // Move to SCREENING state
            ar.setStatus("SCREENING");

            // Load owner and pet entities via EntityService
            Owner owner = null;
            Pet pet = null;

            try {
                if (ar.getOwnerId() != null) {
                    JsonNode ownerNode = entityService.getItem(Owner.ENTITY_NAME, String.valueOf(Owner.ENTITY_VERSION), UUID.fromString(ar.getOwnerId())).join();
                    if (ownerNode != null && !ownerNode.isNull()) {
                        owner = objectMapper.convertValue(ownerNode, Owner.class);
                    }
                }
            } catch (Exception e) {
                logger.warn("Unable to load owner {} during screening: {}", ar.getOwnerId(), e.getMessage());
            }

            try {
                if (ar.getPetId() != null) {
                    JsonNode petNode = entityService.getItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), UUID.fromString(ar.getPetId())).join();
                    if (petNode != null && !petNode.isNull()) {
                        pet = objectMapper.convertValue(petNode, Pet.class);
                    }
                }
            } catch (Exception e) {
                logger.warn("Unable to load pet {} during screening: {}", ar.getPetId(), e.getMessage());
            }

            boolean ownerVerified = false;
            boolean petAvailable = false;

            if (owner != null) {
                // Owner verification in this model is simple: contactEmail presence indicates verified
                ownerVerified = owner.getContactEmail() != null && !owner.getContactEmail().trim().isEmpty();
            }
            if (pet != null) {
                petAvailable = "AVAILABLE".equalsIgnoreCase(pet.getStatus());
            }

            if (ownerVerified && petAvailable && pet != null) {
                // Attempt to reserve pet by updating its status to RESERVED atomically (best-effort)
                try {
                    // Add reservation metadata via tags: reserved_by:<adoptionRequestId>, reserved_until:<iso>
                    List<String> tags = pet.getTags() == null ? new ArrayList<>() : new ArrayList<>(pet.getTags());
                    String reservedByTag = "reserved_by:" + (ar.getId() == null ? ar.getPetId() : ar.getId());
                    String reservedUntilTag = "reserved_until:" + Instant.now().plusSeconds(60 * 60).toString();
                    // Avoid duplications
                    tags.removeIf(t -> t != null && t.startsWith("reserved_by:"));
                    tags.removeIf(t -> t != null && t.startsWith("reserved_until:"));
                    tags.add(reservedByTag);
                    tags.add(reservedUntilTag);
                    pet.setTags(tags);
                    pet.setStatus("RESERVED");

                    // Persist pet reservation via EntityService
                    try {
                        entityService.updateItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), UUID.fromString(pet.getId()), pet).join();
                        ar.setStatus("READY_TO_REVIEW");
                        logger.info("Screening succeeded and reserved pet {} for request {}", pet.getId(), ar.getId());
                    } catch (Exception ex) {
                        logger.warn("Failed to persist reservation for pet {}: {}", pet.getId(), ex.getMessage());
                        ar.setStatus("NEEDS_REVIEW");
                    }

                } catch (Exception e) {
                    logger.error("Error attempting to reserve pet during screening for request {}: {}", ar.getId(), e.getMessage(), e);
                    ar.setStatus("NEEDS_REVIEW");
                }
            } else {
                ar.setStatus("NEEDS_REVIEW");
                logger.info("Screening failed for request {} - ownerVerified={} petAvailable={}", ar.getId(), ownerVerified, petAvailable);
            }

        } catch (Exception e) {
            logger.error("Error during ScreeningProcessor for request {}: {}", ar == null ? "<null>" : ar.getId(), e.getMessage(), e);
        }
        return ar;
    }
}
