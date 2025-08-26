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

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class TagEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TagEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public TagEnrichmentProcessor(SerializerFactory serializerFactory) {
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

        // Collect existing tags (if any)
        List<String> existingTags = entity.getTags() != null ? new ArrayList<>(entity.getTags()) : new ArrayList<>();

        // Use a linked set to preserve insertion order while deduplicating
        LinkedHashSet<String> tagSet = new LinkedHashSet<>();
        for (String t : existingTags) {
            if (t != null && !t.isBlank()) {
                tagSet.add(t.trim().toLowerCase());
            }
        }

        // 1) Add species as a tag (e.g., "cat", "dog")
        if (entity.getSpecies() != null && !entity.getSpecies().isBlank()) {
            tagSet.add(entity.getSpecies().trim().toLowerCase());
        }

        // 2) Add breed tokens as tags
        if (entity.getBreed() != null && !entity.getBreed().isBlank()) {
            String breed = entity.getBreed().trim().toLowerCase();
            // split on non-word characters
            String[] parts = breed.split("\\W+");
            for (String p : parts) {
                if (p != null && p.length() > 1) {
                    tagSet.add(p);
                }
            }
        }

        // 3) Age-based enrichment (simple heuristics)
        Integer age = entity.getAge();
        String species = entity.getSpecies() != null ? entity.getSpecies().trim().toLowerCase() : null;
        if (age != null && age >= 0) {
            // Treat age as years (per model)
            if (age < 1) {
                if ("cat".equals(species)) tagSet.add("kitten");
                else if ("dog".equals(species)) tagSet.add("puppy");
                else tagSet.add("young");
            } else if (age >= 8) {
                tagSet.add("senior");
            } else {
                // general age group
                tagSet.add("adult");
            }
        }

        // 4) Extract keywords from name and description
        StringBuilder textToAnalyze = new StringBuilder();
        if (entity.getName() != null && !entity.getName().isBlank()) {
            textToAnalyze.append(entity.getName()).append(" ");
        }
        if (entity.getDescription() != null && !entity.getDescription().isBlank()) {
            textToAnalyze.append(entity.getDescription()).append(" ");
        }
        if (textToAnalyze.length() > 0) {
            // Basic tokenization and stopword removal
            Set<String> stopwords = Set.of(
                "the","and","for","with","a","an","of","in","on","at","to","is","are","was","were","it","its"
            );
            Pattern splitter = Pattern.compile("\\W+");
            String[] tokens = splitter.split(textToAnalyze.toString().toLowerCase());
            for (String token : tokens) {
                if (token == null) continue;
                token = token.trim();
                if (token.length() <= 2) continue;
                if (stopwords.contains(token)) continue;
                tagSet.add(token);
            }
        }

        // 5) Ensure we have at least one tag; if none, add a fallback tag
        if (tagSet.isEmpty()) {
            tagSet.add("untagged");
        }

        // Convert back to list preserving insertion order
        List<String> enrichedTags = tagSet.stream().collect(Collectors.toList());
        entity.setTags(enrichedTags);

        // 6) Ensure there's at least one image (set a default placeholder if none)
        if (entity.getImages() == null || entity.getImages().isEmpty()) {
            entity.setImages(Collections.singletonList("https://example.com/default-pet.jpg"));
        } else {
            // sanitize images list: remove blanks and trim
            List<String> cleanImages = entity.getImages().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
            if (cleanImages.isEmpty()) {
                cleanImages = Collections.singletonList("https://example.com/default-pet.jpg");
            }
            entity.setImages(cleanImages);
        }

        logger.debug("TagEnrichmentProcessor enriched pet id={} with tags={}", entity.getId(), entity.getTags());

        return entity;
    }
}