package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
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
import java.util.Iterator;
import java.util.List;

@Component
public class PetValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        // Normalize string fields (trim)
        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }
        if (entity.getSpecies() != null) {
            entity.setSpecies(entity.getSpecies().trim());
        }
        if (entity.getBreed() != null) {
            entity.setBreed(entity.getBreed().trim());
        }
        if (entity.getDescription() != null) {
            entity.setDescription(entity.getDescription().trim());
        }
        if (entity.getSourceId() != null) {
            entity.setSourceId(entity.getSourceId().trim());
        }
        if (entity.getSourceUrl() != null) {
            entity.setSourceUrl(entity.getSourceUrl().trim());
        }
        if (entity.getStatus() != null) {
            entity.setStatus(entity.getStatus().trim());
        }

        // Ensure photos list is not null and remove blank entries
        List<String> photos = entity.getPhotos();
        if (photos == null) {
            photos = new ArrayList<>();
            entity.setPhotos(photos);
        } else {
            Iterator<String> it = photos.iterator();
            List<String> cleaned = new ArrayList<>();
            while (it.hasNext()) {
                String p = it.next();
                if (p != null) {
                    p = p.trim();
                }
                if (p != null && !p.isBlank()) {
                    cleaned.add(p);
                }
            }
            entity.setPhotos(cleaned);
        }

        // Business logic:
        // If pet has no external source (no sourceId) then it does not require enrichment
        // and can be made available for adoption if not already set to a meaningful status.
        // If sourceId is present, leave status unchanged so enrichment processor can run.
        String sourceId = entity.getSourceId();
        if (sourceId == null || sourceId.isBlank()) {
            // Only set to "available" if current status is blank or looks like an initial state.
            // We avoid overwriting explicit statuses like reserved/adopted/archived.
            String status = entity.getStatus();
            if (status == null || status.isBlank()) {
                entity.setStatus("available");
                logger.info("Pet [{}] has no sourceId; marking as available", entity.getId());
            }
        } else {
            logger.info("Pet [{}] has sourceId [{}]; enrichment required", entity.getId(), sourceId);
        }

        // Age sanity check: if null leave as-is; negative ages were already filtered by isValid()
        // No persistence calls here; Cyoda will persist the entity state automatically.

        return entity;
    }
}