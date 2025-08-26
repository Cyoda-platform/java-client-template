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

@Component
public class TransformPetDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransformPetDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public TransformPetDataProcessor(SerializerFactory serializerFactory) {
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

        // 1. Ensure technical id exists
        if (entity.getId() == null || entity.getId().isBlank()) {
            entity.setId(UUID.randomUUID().toString());
        }

        // 2. Normalize name
        if (entity.getName() != null) {
            String normalized = normalizeName(entity.getName());
            entity.setName(normalized);
        }

        // 3. Normalize species mapping and ensure non-blank
        String species = entity.getSpecies();
        species = mapSpecies(species);
        if (species == null || species.isBlank()) {
            species = "unknown";
        }
        entity.setSpecies(species);

        // 4. Ensure breed present
        if (entity.getBreed() == null || entity.getBreed().isBlank()) {
            entity.setBreed("unknown");
        } else {
            entity.setBreed(entity.getBreed().trim());
        }

        // 5. Validate age: if negative -> remove, attempt to derive from sourceMetadata
        Integer age = entity.getAge();
        if (age != null && age < 0) {
            age = null;
        }
        if (age == null) {
            // try to extract from sourceMetadata.raw if present
            try {
                Pet.SourceMetadata sm = entity.getSourceMetadata();
                if (sm != null && sm.getRaw() != null) {
                    Object rawAge = sm.getRaw().get("age");
                    if (rawAge instanceof Number) {
                        age = ((Number) rawAge).intValue();
                        if (age < 0) age = null;
                    } else if (rawAge instanceof String) {
                        String s = ((String) rawAge).trim();
                        if (!s.isBlank()) {
                            try {
                                int parsed = Integer.parseInt(s);
                                if (parsed >= 0) age = parsed;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Unable to extract age from sourceMetadata: {}", e.getMessage());
            }
        }
        entity.setAge(age);

        // 6. Ensure healthNotes is non-null (empty string if absent)
        if (entity.getHealthNotes() == null) {
            entity.setHealthNotes("");
        } else {
            entity.setHealthNotes(entity.getHealthNotes().trim());
        }

        // 7. Derive tags heuristically if none present
        List<String> tags = entity.getTags();
        if (tags == null) {
            tags = new ArrayList<>();
            // basic heuristics
            String nm = entity.getName() != null ? entity.getName().toLowerCase() : "";
            String br = entity.getBreed() != null ? entity.getBreed().toLowerCase() : "";
            String sp = entity.getSpecies() != null ? entity.getSpecies().toLowerCase() : "";

            if (sp.contains("cat")) {
                tags.add("cat");
                if (br.contains("siamese")) tags.add("lap cat");
            } else if (sp.contains("dog")) {
                tags.add("dog");
                if (br.contains("labrador") || br.contains("retriever")) tags.add("friendly");
            } else if (sp.contains("rabbit")) {
                tags.add("rabbit");
            } else {
                tags.add("unknown-species");
            }

            if (nm.contains("play") || nm.contains("playful")) tags.add("playful");
            if (entity.getAge() != null && entity.getAge() <= 1) tags.add("young");
            if (!entity.getHealthNotes().isBlank()) tags.add("health-notes");

            // dedupe and clean
            Set<String> tagSet = new LinkedHashSet<>();
            for (String t : tags) {
                if (t != null) {
                    String cleaned = t.trim().toLowerCase();
                    if (!cleaned.isBlank()) tagSet.add(cleaned);
                }
            }
            tags = new ArrayList<>(tagSet);
        } else {
            // clean existing tags: trim, lowercase, remove blanks and duplicates
            Set<String> tagSet = new LinkedHashSet<>();
            for (String t : tags) {
                if (t != null) {
                    String cleaned = t.trim().toLowerCase();
                    if (!cleaned.isBlank()) tagSet.add(cleaned);
                }
            }
            tags = new ArrayList<>(tagSet);
        }
        entity.setTags(tags);

        // 8. Ensure sourceMetadata.petstoreId is set if available in raw
        try {
            Pet.SourceMetadata sm = entity.getSourceMetadata();
            if (sm != null) {
                if ((sm.getPetstoreId() == null || sm.getPetstoreId().isBlank()) && sm.getRaw() != null) {
                    Object possibleId = sm.getRaw().get("id");
                    if (possibleId == null) possibleId = sm.getRaw().get("petstoreId");
                    if (possibleId instanceof String) {
                        String pid = ((String) possibleId).trim();
                        if (!pid.isBlank()) sm.setPetstoreId(pid);
                    } else if (possibleId != null) {
                        sm.setPetstoreId(String.valueOf(possibleId));
                    }
                    entity.setSourceMetadata(sm);
                }
            }
        } catch (Exception e) {
            logger.debug("Unable to normalize sourceMetadata: {}", e.getMessage());
        }

        // 9. Ensure non-blank status. Default to PENDING_REVIEW (initial import state)
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("PENDING_REVIEW");
        } else {
            entity.setStatus(entity.getStatus().trim().toUpperCase());
        }

        // Final validation log
        if (!entity.isValid()) {
            logger.warn("Transformed pet entity is not fully valid after transformation: id={}", entity.getId());
        } else {
            logger.debug("Transformed pet entity ready: id={}, name={}, status={}", entity.getId(), entity.getName(), entity.getStatus());
        }

        return entity;
    }

    // Helper: normalize name (trim, collapse spaces, title case)
    private String normalizeName(String name) {
        if (name == null) return null;
        String trimmed = name.trim().replaceAll("\\s+", " ");
        String[] parts = trimmed.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isBlank()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1).toLowerCase());
            if (i < parts.length - 1) sb.append(" ");
        }
        return sb.toString();
    }

    // Helper: map species synonyms to canonical values
    private String mapSpecies(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toLowerCase();
        if (s.equals("kitty") || s.equals("feline")) return "cat";
        if (s.equals("puppy") || s.equals("canine")) return "dog";
        if (s.startsWith("cat")) return "cat";
        if (s.startsWith("dog")) return "dog";
        if (s.contains("rabbit")) return "rabbit";
        if (s.contains("bird")) return "bird";
        return s;
    }
}