package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Component
public class PersistPetEventProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistPetEventProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PersistPetEventProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        if (entity == null) return false;
        // Allow ingestion to provide pets missing some optional fields (status/importedAt will be set here).
        // ID may not be available on all Pet versions; validate based on required visible fields.
        if (entity.getName() == null || entity.getName().isBlank()) return false;
        if (entity.getSpecies() == null || entity.getSpecies().isBlank()) return false;
        return true;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        try {
            // Normalize and provide defaults without persisting via EntityService (the workflow will persist this entity)
            // Default status for newly persisted pets by ingestion pipeline
            if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                entity.setStatus("PERSISTED");
            }

            // Default source if missing
            if (entity.getSource() == null || entity.getSource().isBlank()) {
                entity.setSource("Petstore");
            }

            // Normalize textual fields
            if (entity.getSpecies() != null) {
                entity.setSpecies(entity.getSpecies().trim().toLowerCase());
            }
            if (entity.getName() != null) {
                entity.setName(entity.getName().trim());
            }
            if (entity.getBreed() != null) {
                entity.setBreed(entity.getBreed().trim());
            }
            if (entity.getSex() != null) {
                entity.setSex(entity.getSex().trim().toUpperCase());
            }
            if (entity.getAge() != null) {
                entity.setAge(entity.getAge().trim());
            }
            if (entity.getBio() != null) {
                entity.setBio(entity.getBio().trim());
            }
            if (entity.getHealthNotes() == null) {
                entity.setHealthNotes("");
            } else {
                entity.setHealthNotes(entity.getHealthNotes().trim());
            }

            // Ensure collections are non-null and cleaned
            if (entity.getPhotos() == null) {
                entity.setPhotos(new ArrayList<>());
            } else {
                List<String> cleanedPhotos = new ArrayList<>();
                for (String p : entity.getPhotos()) {
                    if (p != null && !p.isBlank()) cleanedPhotos.add(p.trim());
                }
                entity.setPhotos(cleanedPhotos);
            }

            if (entity.getTags() == null) {
                entity.setTags(new ArrayList<>());
            } else {
                List<String> cleanedTags = new ArrayList<>();
                for (String t : entity.getTags()) {
                    if (t != null && !t.isBlank()) cleanedTags.add(t.trim().toLowerCase());
                }
                entity.setTags(cleanedTags);
            }

            // Ensure size has a value (ingestion may leave it empty)
            if (entity.getSize() == null || entity.getSize().isBlank()) {
                entity.setSize("unknown");
            }

            logger.info("Pet normalized for persistence. name={}, status={}", entity.getName(), entity.getStatus());
        } catch (Exception ex) {
            // Do not throw; attach error info via logs. The framework will handle persistence of the modified entity.
            logger.error("Error while processing pet entity name={}: {}", entity != null ? entity.getName() : "null", ex.getMessage(), ex);
        }

        return entity;
    }
}