package com.java_template.application.processor;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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

        // Business rule: If the pet source indicates it comes from Petstore, enrich missing optional fields.
        // Source matching is case-insensitive and checks for the substring "petstore".
        String source = entity.getSource();
        boolean isPetstoreSource = source != null && source.toLowerCase().contains("petstore");

        if (isPetstoreSource) {
            logger.info("Enriching pet (id={}) from Petstore source '{}'", entity.getId(), source);

            // Ensure photos list is non-null (Pet.isValid requires non-null photos)
            if (entity.getPhotos() == null) {
                entity.setPhotos(new ArrayList<>());
                logger.debug("Set empty photos list for pet id={}", entity.getId());
            }

            // Enrich species if missing
            if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
                entity.setSpecies("unknown");
                logger.debug("Enriched species to 'unknown' for pet id={}", entity.getId());
            }

            // Enrich breed if missing
            if (entity.getBreed() == null || entity.getBreed().isBlank()) {
                entity.setBreed("unknown");
                logger.debug("Enriched breed to 'unknown' for pet id={}", entity.getId());
            }

            // Enrich description if missing
            if (entity.getDescription() == null || entity.getDescription().isBlank()) {
                entity.setDescription("No description provided");
                logger.debug("Enriched description for pet id={}", entity.getId());
            }

            // Enrich age if missing (default to 0)
            if (entity.getAge() == null) {
                entity.setAge(0);
                logger.debug("Enriched age to 0 for pet id={}", entity.getId());
            }

            // Enrich gender if missing
            if (entity.getGender() == null || entity.getGender().isBlank()) {
                entity.setGender("unknown");
                logger.debug("Enriched gender to 'unknown' for pet id={}", entity.getId());
            }

            // Ensure status is set; default to "available" for Petstore entries
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