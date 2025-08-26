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

        if (entity == null) {
            return null;
        }

        try {
            // 1) Normalize age:
            normalizeAge(entity);

            // 2) Enrich temperament tags:
            enrichTemperamentTags(entity);

            // 3) Map region/state transitions:
            mapAvailabilityState(entity);

            // 4) Ensure location postal is trimmed or set to empty string (no invented fields)
            if (entity.getLocation() != null) {
                String postal = entity.getLocation().getPostal();
                if (postal != null) {
                    postal = postal.trim();
                    entity.getLocation().setPostal(postal.isEmpty() ? null : postal);
                }
            }
        } catch (Exception ex) {
            logger.warn("EnrichmentProcessor encountered an exception while processing pet id={} : {}", entity.getId(), ex.getMessage());
            // Do not rethrow - leave entity unchanged apart from partial enrichment
        }

        return entity;
    }

    private void normalizeAge(Pet pet) {
        if (pet.getAge_unit() == null || pet.getAge_value() == null) return;

        String unit = pet.getAge_unit().trim().toLowerCase();
        Integer value = pet.getAge_value();

        if ("months".equals(unit)) {
            if (value >= 12) {
                int years = value / 12;
                pet.setAge_value(years);
                pet.setAge_unit("years");
            } else {
                // keep as months (explicit normalized form)
                pet.setAge_unit("months");
            }
        } else if ("years".equals(unit)) {
            // sanitize unit to canonical lowercase "years"
            pet.setAge_unit("years");
            if (value < 0) {
                pet.setAge_value(0);
            }
        } else {
            // Unknown unit: try to fallback to years
            pet.setAge_unit("years");
            if (value < 0) pet.setAge_value(0);
        }
    }

    private void enrichTemperamentTags(Pet pet) {
        List<String> tags = pet.getTemperament_tags();
        if (tags == null) {
            tags = new ArrayList<>();
            pet.setTemperament_tags(tags);
        }

        Set<String> dedup = new LinkedHashSet<>();
        // preserve existing tags (trimmed)
        for (String t : tags) {
            if (t != null) {
                String tt = t.trim().toLowerCase();
                if (!tt.isEmpty()) dedup.add(tt);
            }
        }

        // Infer from species
        String species = pet.getSpecies();
        if (species != null) {
            String s = species.trim().toLowerCase();
            if ("dog".equals(s)) dedup.add("friendly");
            else if ("cat".equals(s)) dedup.add("independent");
            else dedup.add("other-species");
        }

        // Infer from size
        String size = pet.getSize();
        if (size != null) {
            String sz = size.trim().toLowerCase();
            if (!sz.isEmpty()) dedup.add(sz);
        }

        // Infer from health_status
        String health = pet.getHealth_status();
        if (health != null && "vaccinated".equalsIgnoreCase(health.trim())) {
            dedup.add("vaccinated");
        }

        // Age-related tags
        if (pet.getAge_value() != null && pet.getAge_unit() != null) {
            double years;
            if ("months".equalsIgnoreCase(pet.getAge_unit())) {
                years = pet.getAge_value() / 12.0;
            } else {
                years = pet.getAge_value();
            }
            if (years < 1.0) dedup.add("young");
            else if (years >= 8.0) dedup.add("senior");
            else dedup.add("adult");
        }

        // Convert dedup set back to list with reasonable casing
        List<String> finalTags = new ArrayList<>();
        for (String t : dedup) {
            finalTags.add(t);
        }

        pet.setTemperament_tags(finalTags);
    }

    private void mapAvailabilityState(Pet pet) {
        String status = pet.getAvailability_status();
        if (status == null) return;
        String s = status.trim();
        if (s.equalsIgnoreCase("PENDING_VERIFICATION")) {
            pet.setAvailability_status("AWAITING_APPROVAL");
        }
        // Do not update other entities or call external services here.
    }
}