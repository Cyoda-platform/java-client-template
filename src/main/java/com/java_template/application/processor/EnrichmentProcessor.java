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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class EnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichmentProcessor(SerializerFactory serializerFactory) {
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

        try {
            // 1) Normalize age: convert months -> years when >= 12 months
            Integer ageValue = entity.getAge_value();
            String ageUnit = entity.getAge_unit();
            if (ageValue != null && ageUnit != null) {
                if ("months".equalsIgnoreCase(ageUnit) && ageValue >= 12) {
                    int years = ageValue / 12;
                    if (years <= 0) {
                        // keep as months if conversion would be zero (defensive)
                        entity.setAge_value(ageValue);
                        entity.setAge_unit("months");
                    } else {
                        entity.setAge_value(years);
                        entity.setAge_unit("years");
                    }
                } else {
                    // normalize unit casing only
                    entity.setAge_unit(ageUnit.trim().toLowerCase());
                }
            }

            // 2) Infer temperament tags (augment existing tags, avoid duplicates)
            List<String> currentTags = entity.getTemperament_tags();
            Set<String> tagSet = new LinkedHashSet<>();
            if (currentTags != null) {
                for (String t : currentTags) {
                    if (t != null && !t.isBlank()) tagSet.add(t.trim().toLowerCase());
                }
            } else {
                // ensure non-null list as entity.isValid() requires non-null; defensive fallback
                currentTags = new ArrayList<>();
                entity.setTemperament_tags(currentTags);
            }

            // Add tags based on species
            String species = entity.getSpecies();
            if (species != null) {
                if ("dog".equalsIgnoreCase(species)) {
                    tagSet.add("friendly");
                } else if ("cat".equalsIgnoreCase(species)) {
                    tagSet.add("independent");
                } else {
                    tagSet.add("other-species");
                }
            }

            // Add tag for young pets (age < 1 year)
            String normalizedAgeUnit = entity.getAge_unit();
            Integer normalizedAgeValue = entity.getAge_value();
            boolean consideredYoung = false;
            if (normalizedAgeUnit != null && normalizedAgeValue != null) {
                if ("months".equalsIgnoreCase(normalizedAgeUnit) && normalizedAgeValue < 12) {
                    consideredYoung = true;
                } else if ("years".equalsIgnoreCase(normalizedAgeUnit) && normalizedAgeValue != null && normalizedAgeValue < 1) {
                    consideredYoung = true;
                }
            }
            if (consideredYoung) {
                tagSet.add("young");
            }

            // If size suggests energetic/small/large, map to tags
            String size = entity.getSize();
            if (size != null) {
                String s = size.trim().toLowerCase();
                if ("small".equalsIgnoreCase(s)) tagSet.add("small");
                else if ("medium".equalsIgnoreCase(s)) tagSet.add("medium");
                else if ("large".equalsIgnoreCase(s)) tagSet.add("large");
            }

            // Persist back unique ordered tag list
            List<String> finalTags = new ArrayList<>(tagSet);
            entity.setTemperament_tags(finalTags);

            // 3) Normalize size casing/trimming
            if (entity.getSize() != null) {
                entity.setSize(entity.getSize().trim().toLowerCase());
            }

            // 4) Clean and normalize location fields (trim city/postal)
            Pet.Location loc = entity.getLocation();
            if (loc != null) {
                if (loc.getCity() != null) {
                    loc.setCity(loc.getCity().trim());
                }
                if (loc.getPostal() != null) {
                    loc.setPostal(loc.getPostal().trim());
                }
                // leave lat/lon as-is; no geocoding here
                entity.setLocation(loc);
            }

            // 5) Deduplicate photos while preserving order
            List<String> photos = entity.getPhotos();
            if (photos != null) {
                Set<String> photoSet = new LinkedHashSet<>();
                for (String p : photos) {
                    if (p != null && !p.isBlank()) photoSet.add(p.trim());
                }
                entity.setPhotos(new ArrayList<>(photoSet));
            }

            // 6) Basic enrichment for health_status: normalize casing if present
            if (entity.getHealth_status() != null) {
                entity.setHealth_status(entity.getHealth_status().trim().toLowerCase());
            }

            // 7) Ensure availability_status normalized
            if (entity.getAvailability_status() != null) {
                entity.setAvailability_status(entity.getAvailability_status().trim().toLowerCase());
            }

        } catch (Exception ex) {
            logger.warn("Error during enrichment for pet id {}: {}", entity.getId(), ex.getMessage());
            // Do not change entity state in case of unexpected errors beyond logging
        }

        return entity;
    }
}