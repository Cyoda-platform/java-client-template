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

@Component
public class EnrichPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichPetProcessor(SerializerFactory serializerFactory) {
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
        if (entity == null) return null;

        // Trim basic string fields if present
        try {
            if (entity.getName() != null) entity.setName(entity.getName().trim());
            if (entity.getSpecies() != null) entity.setSpecies(entity.getSpecies().trim());
            if (entity.getBreed() != null) entity.setBreed(entity.getBreed().trim());
            if (entity.getDescription() != null) entity.setDescription(entity.getDescription().trim());
            if (entity.getGender() != null) entity.setGender(entity.getGender().trim());
            if (entity.getStatus() != null) entity.setStatus(entity.getStatus().trim());
        } catch (Exception e) {
            logger.debug("Error trimming fields for pet: {}", e.getMessage());
        }

        String source = entity.getSource();
        if (source != null && source.toLowerCase().contains("petstore")) {
            logger.info("Enriching pet (id={}) from Petstore source '{}'", entity.getId(), source);

            if (entity.getPhotos() == null) {
                entity.setPhotos(new ArrayList<>());
                logger.debug("Set empty photos list for pet id={}", entity.getId());
            }

            if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
                entity.setSpecies("unknown");
                logger.debug("Enriched species to 'unknown' for pet id={}", entity.getId());
            }

            if (entity.getBreed() == null || entity.getBreed().isBlank()) {
                entity.setBreed("unknown");
                logger.debug("Enriched breed to 'unknown' for pet id={}", entity.getId());
            }

            if (entity.getDescription() == null || entity.getDescription().isBlank()) {
                entity.setDescription("No description provided");
                logger.debug("Enriched description for pet id={}", entity.getId());
            }

            if (entity.getAge() == null) {
                entity.setAge(0);
                logger.debug("Enriched age to 0 for pet id={}", entity.getId());
            } else if (entity.getAge() < 0) {
                // Normalize negative ages to null so validation can mark invalid if needed
                logger.warn("Pet {} has negative age {}; normalizing to null", entity.getId(), entity.getAge());
                entity.setAge(null);
            }

            if (entity.getGender() == null || entity.getGender().isBlank()) {
                entity.setGender("unknown");
                logger.debug("Enriched gender to 'unknown' for pet id={}", entity.getId());
            }

            if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                entity.setStatus("available");
                logger.debug("Enriched status to 'available' for pet id={}", entity.getId());
            }
        } else {
            logger.debug("Pet id={} not from Petstore (source='{}'), skipping enrichment", entity.getId(), source);
        }

        return entity;
    }
}