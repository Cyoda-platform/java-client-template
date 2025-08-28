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

import java.lang.reflect.Field;
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

    @SuppressWarnings("unchecked")
    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        if (entity == null) return null;

        try {
            // Access fields reflectively to avoid relying on generated getters/setters
            List<String> existingTags = getField(entity, "tags", List.class);
            if (existingTags == null) {
                existingTags = new ArrayList<>();
            }

            // Use a LinkedHashSet to deduplicate while preserving insertion order
            Set<String> tagSet = new LinkedHashSet<>(existingTags);

            // Add species-based tags
            String species = getField(entity, "species", String.class);
            String speciesLower = species != null ? species.toLowerCase() : "";
            if (speciesLower.contains("cat")) {
                tagSet.add("playful");
                tagSet.add("lapcat");
            } else if (speciesLower.contains("dog")) {
                tagSet.add("playful");
                tagSet.add("loyal");
            } else if (!speciesLower.isBlank()) {
                tagSet.add("friendly");
            } else {
                tagSet.add("companion");
            }

            // Add age-related tag
            Integer age = getField(entity, "age", Integer.class);
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
            String importedFrom = getField(entity, "importedFrom", String.class);
            if (importedFrom != null && !importedFrom.isBlank()) {
                tagSet.add("imported");
            }

            // Persist tags back to the entity (reflective set)
            setField(entity, "tags", new ArrayList<>(tagSet));

            // Generate a friendly description if missing or blank
            String desc = getField(entity, "description", String.class);
            if (desc == null || desc.isBlank()) {
                String name = getField(entity, "name", String.class);
                name = (name != null && !name.isBlank()) ? name : "This pet";
                String breed = getField(entity, "breed", String.class);
                breed = (breed != null && !breed.isBlank()) ? breed : "";
                String speciesText = (species != null && !species.isBlank()) ? species : "pet";
                String ageText = (age == null) ? "of unknown age" : ("about " + age + " year" + (age == 1 ? "" : "s") + " old");
                String imported = (importedFrom != null && !importedFrom.isBlank()) ? " Imported from " + importedFrom + "." : "";
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

                setField(entity, "description", sb.toString().trim());
            }

            // Do not change availability/status here — publishing processor will set status AVAILABLE.
            String petId = getField(entity, "petId", String.class);
            logger.info("Enriched pet {} with tags={} and description present={}", petId, getField(entity, "tags", List.class), getField(entity, "description", String.class) != null && !getField(entity, "description", String.class).isBlank());
        } catch (Exception ex) {
            // Try to log petId if available
            String pid = null;
            try { pid = getField(entity, "petId", String.class); } catch (Exception e) { /* ignore */ }
            logger.error("Failed to enrich Pet {}: {}", pid != null ? pid : "unknown", ex.getMessage(), ex);
        }

        return entity;
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Object target, String fieldName, Class<T> clazz) {
        if (target == null) return null;
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            Object val = f.get(target);
            if (val == null) return null;
            if (clazz.isInstance(val)) {
                return (T) val;
            }
            // handle primitive wrappers and casting
            return clazz.cast(val);
        } catch (Exception e) {
            logger.debug("Unable to read field '{}' on {}: {}", fieldName, target.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private boolean setField(Object target, String fieldName, Object value) {
        if (target == null) return false;
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) return false;
            f.setAccessible(true);
            f.set(target, value);
            return true;
        } catch (Exception e) {
            logger.debug("Unable to set field '{}' on {}: {}", fieldName, target.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    private Field findField(Class<?> cls, String name) {
        Class<?> current = cls;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}