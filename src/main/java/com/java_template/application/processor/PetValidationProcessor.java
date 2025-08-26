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

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        if (entity == null) {
            return null;
        }

        // Normalize string fields: trim values to avoid accidental blanks
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

        // Clean up photos list: remove null/blank entries and trim urls
        List<String> photos = entity.getPhotos();
        if (photos != null) {
            List<String> cleaned = new ArrayList<>();
            for (String p : photos) {
                if (p != null) {
                    String t = p.trim();
                    if (!t.isBlank()) {
                        cleaned.add(t);
                    }
                }
            }
            entity.setPhotos(cleaned);
        }

        // Business rule: age must be non-negative - if negative, nullify to let validation catch or adjust to 0
        if (entity.getAge() != null && entity.getAge() < 0) {
            // Prefer to nullify invalid age so overall validation can react upstream; here we set to null
            entity.setAge(null);
        }

        // Business rule: if pet appears fully valid and status is a transient 'created' or 'validating', promote to 'available'
        // (Only adjust status when it's a known transient state)
        String st = entity.getStatus();
        if (st != null) {
            String lower = st.toLowerCase();
            if (lower.equals("created") || lower.equals("validating")) {
                entity.setStatus("available");
            }
        }

        // Return the possibly-modified entity; Cyoda will persist it automatically
        return entity;
    }
}