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
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class PetEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetEnrichmentProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        if (entity == null) return null;

        try {
            // Ensure tags list exists
            List<String> existingTags = entity.getTags();
            if (existingTags == null) {
                existingTags = new ArrayList<>();
            }

            // Use a LinkedHashSet to deduplicate while preserving insertion order
            Set<String> tagSet = new LinkedHashSet<>(existingTags);

            // Add species-based tags
            String species = entity.getSpecies() != null ? entity.getSpecies().toLowerCase() : "";
            if (species.contains("cat")) {
                tagSet.add("playful");
                tagSet.add("lapcat");
            } else if (species.contains("dog")) {
                tagSet.add("playful");
                tagSet.add("loyal");
            } else if (!species.isBlank()) {
                tagSet.add("friendly");
            } else {
                tagSet.add("companion");
            }

            // Add age-related tag
            Integer age = entity.getAge();
            if (age != null) {
                if (age < 1) {
                    tagSet.add("young");
                } else if (age < 3) {
                    tagSet.add("juvenile");
                } else if (age < 7) {
                    tagSet.add("adult");
                } else {
                    tagSet.add("senior");
                }
            } else {
                tagSet.add("age_unknown");
            }

            // Add a tag indicating imported source if available
            if (entity.getImportedFrom() != null && !entity.getImportedFrom().isBlank()) {
                tagSet.add("imported");
            }

            // Persist tags back to the entity
            entity.setTags(new ArrayList<>(tagSet));

            // Generate a friendly description if missing or blank
            String desc = entity.getDescription();
            if (desc == null || desc.isBlank()) {
                String name = (entity.getName() != null && !entity.getName().isBlank()) ? entity.getName() : "This pet";
                String breed = (entity.getBreed() != null && !entity.getBreed().isBlank()) ? entity.getBreed() : "";
                String speciesText = (entity.getSpecies() != null && !entity.getSpecies().isBlank()) ? entity.getSpecies() : "pet";
                String ageText = (age == null) ? "of unknown age" : ("about " + age + " year" + (age == 1 ? "" : "s") + " old");
                String imported = (entity.getImportedFrom() != null && !entity.getImportedFrom().isBlank()) ? " Imported from " + entity.getImportedFrom() + "." : "";
                String tagSummary = String.join(", ", tagSet);

                StringBuilder sb = new StringBuilder();
                if (!breed.isBlank()) {
                    sb.append(String.format("%s is a %s %s %s.", name, breed, speciesText, ageText));
                } else {
                    sb.append(String.format("%s is a %s %s.", name, speciesText, ageText));
                }
                sb.append(" ");
                sb.append("Looks like a ").append(tagSummary).append(".");
                sb.append(imported);

                entity.setDescription(sb.toString().trim());
            }

            // Do not change availability/status here — publishing processor will set status AVAILABLE.
            logger.info("Enriched pet {} with tags={} and description present={}", entity.getPetId(), entity.getTags(), entity.getDescription() != null && !entity.getDescription().isBlank());
        } catch (Exception ex) {
            logger.error("Failed to enrich Pet {}: {}", entity.getPetId(), ex.getMessage(), ex);
        }

        return entity;
    }
}