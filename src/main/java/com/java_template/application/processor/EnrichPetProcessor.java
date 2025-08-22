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

import java.time.Instant;
import java.util.Objects;

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

        // Map external metadata into description when externalId is present
        if (entity.getExternalId() != null && !entity.getExternalId().isBlank()) {
            String existing = entity.getDescription();
            if (existing == null) existing = "";
            String source = entity.getSource() != null ? entity.getSource() : "external";
            String append = "Source: " + source;
            if (existing.isBlank()) {
                entity.setDescription(append);
            } else if (!existing.contains(append)) {
                entity.setDescription(existing + " " + append);
            }
        }

        // Compute age category from ageMonths and append to description (Pet entity doesn't have ageCategory field)
        String ageCategory = computeAgeCategory(entity.getAgeMonths());
        String desc = entity.getDescription();
        if (desc == null) desc = "";
        String ageTag = "AgeCategory: " + ageCategory;
        if (desc.isBlank()) {
            entity.setDescription(ageTag);
        } else if (!desc.contains(ageTag)) {
            entity.setDescription(desc + " " + ageTag);
        }

        // Update timestamp
        entity.setUpdatedAt(Instant.now().toString());

        return entity;
    }

    private String computeAgeCategory(Integer ageMonths) {
        if (ageMonths == null) return "unknown";
        if (ageMonths < 6) return "puppy";
        if (ageMonths < 24) return "young";
        if (ageMonths < 84) return "adult";
        return "senior";
    }
}