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
import java.util.Objects;

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

        if (entity == null) return null;

        // Trim and normalize common string fields
        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }
        if (entity.getSpecies() != null) {
            entity.setSpecies(entity.getSpecies().trim().toLowerCase());
        }
        if (entity.getBreed() != null) {
            entity.setBreed(entity.getBreed().trim());
        }
        if (entity.getStatus() != null) {
            entity.setStatus(entity.getStatus().trim());
        }
        if (entity.getSourceId() != null) {
            entity.setSourceId(entity.getSourceId().trim());
        }
        if (entity.getDescription() != null) {
            entity.setDescription(entity.getDescription().trim());
        }

        // Ensure collections are non-null and contain no blank entries
        List<String> tags = entity.getTags();
        if (tags == null) {
            tags = new ArrayList<>();
        } else {
            Iterator<String> it = tags.iterator();
            while (it.hasNext()) {
                String t = it.next();
                if (t == null || t.isBlank()) {
                    it.remove();
                } else {
                    // normalize tag whitespace
                    String nt = t.trim();
                    if (!Objects.equals(nt, t)) {
                        it.remove();
                        if (!nt.isBlank()) tags.add(nt);
                    }
                }
            }
        }
        entity.setTags(tags);

        List<String> images = entity.getImages();
        if (images == null) {
            images = new ArrayList<>();
        } else {
            Iterator<String> it = images.iterator();
            while (it.hasNext()) {
                String img = it.next();
                if (img == null || img.isBlank()) {
                    it.remove();
                } else {
                    String ni = img.trim();
                    if (!Objects.equals(ni, img)) {
                        it.remove();
                        if (!ni.isBlank()) images.add(ni);
                    }
                }
            }
        }
        entity.setImages(images);

        // Sanity check for age
        if (entity.getAge() != null && entity.getAge() < 0) {
            logger.warn("Pet {} had negative age {}. Normalizing to 0.", entity.getId(), entity.getAge());
            entity.setAge(0);
        }

        // Basic validation result mapping:
        // If core required fields are missing after normalization, mark status as "invalid"
        boolean coreMissing = (entity.getName() == null || entity.getName().isBlank())
                || (entity.getSpecies() == null || entity.getSpecies().isBlank())
                || (entity.getStatus() == null || entity.getStatus().isBlank())
                || (entity.getId() == null || entity.getId().isBlank())
                || (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank())
                || (entity.getUpdatedAt() == null || entity.getUpdatedAt().isBlank());

        if (coreMissing) {
            logger.info("Pet {} failed validation of required fields. Marking status=invalid.", entity.getId());
            entity.setStatus("invalid");
            // Append short validation note to description (do not overwrite existing valuable data)
            String note = "Validation failed: missing required core fields.";
            if (entity.getDescription() == null || entity.getDescription().isBlank()) {
                entity.setDescription(note);
            } else if (!entity.getDescription().contains(note)) {
                entity.setDescription(entity.getDescription() + " " + note);
            }
        } else {
            // If everything looks sane, ensure status is not an accidental blank and keep it
            if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                entity.setStatus("available");
            }
        }

        // Final normalization: ensure lists are never null
        if (entity.getTags() == null) entity.setTags(new ArrayList<>());
        if (entity.getImages() == null) entity.setImages(new ArrayList<>());

        return entity;
    }
}