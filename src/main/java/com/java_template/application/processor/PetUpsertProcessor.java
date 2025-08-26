package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
public class PetUpsertProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetUpsertProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetUpsertProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        try {
            String now = Instant.now().toString();

            // Attempt to find existing pet by sourceId (if provided)
            Pet existing = null;
            if (entity.getSourceId() != null && !entity.getSourceId().isBlank()) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.sourceId", "EQUALS", entity.getSourceId())
                );
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    condition,
                    true
                );
                ArrayNode results = itemsFuture.get();
                if (results != null && results.size() > 0) {
                    ObjectNode first = (ObjectNode) results.get(0);
                    existing = objectMapper.treeToValue(first, Pet.class);
                }
            }

            boolean isNew = (existing == null);
            boolean changed = false;

            if (isNew) {
                // New pet: ensure timestamps are set
                if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
                    entity.setCreatedAt(now);
                }
                if (entity.getUpdatedAt() == null || entity.getUpdatedAt().isBlank()) {
                    entity.setUpdatedAt(now);
                }
                changed = true; // treat as change to kick off notifications
            } else {
                // Merge existing -> preserve technical id and createdAt
                entity.setId(existing.getId());
                if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
                    entity.setCreatedAt(existing.getCreatedAt());
                }

                // Merge images
                List<String> mergedImages = mergeLists(existing.getImages(), entity.getImages());
                entity.setImages(mergedImages);

                // Merge tags
                List<String> mergedTags = mergeLists(existing.getTags(), entity.getTags());
                entity.setTags(mergedTags);

                // Ensure updatedAt
                if (entity.getUpdatedAt() == null || entity.getUpdatedAt().isBlank()) {
                    entity.setUpdatedAt(now);
                }

                // Detect changes on primary fields
                if (!safeEquals(existing.getName(), entity.getName())) changed = true;
                if (!safeEquals(existing.getDescription(), entity.getDescription())) changed = true;
                if (!Objects.equals(existing.getAge(), entity.getAge())) changed = true;
                if (!safeEquals(existing.getBreed(), entity.getBreed())) changed = true;
                if (!safeEquals(existing.getSpecies(), entity.getSpecies())) changed = true;
                if (!safeEquals(existing.getStatus(), entity.getStatus())) changed = true;
                if (!safeEquals(existing.getSourceUpdatedAt(), entity.getSourceUpdatedAt())) changed = true;
                if (!listEquals(existing.getImages(), entity.getImages())) changed = true;
                if (!listEquals(existing.getTags(), entity.getTags())) changed = true;
            }

            // If change detected -> create notify Jobs for matching subscribers
            if (changed) {
                try {
                    CompletableFuture<ArrayNode> subsFuture = entityService.getItems(
                        Subscriber.ENTITY_NAME,
                        String.valueOf(Subscriber.ENTITY_VERSION)
                    );
                    ArrayNode subsArray = subsFuture.get();
                    if (subsArray != null) {
                        for (int i = 0; i < subsArray.size(); i++) {
                            ObjectNode subNode = (ObjectNode) subsArray.get(i);
                            Subscriber sub = objectMapper.treeToValue(subNode, Subscriber.class);

                            if (!sub.isActive()) continue;
                            if (!sub.isVerified()) continue;
                            if (matchesSubscriberPreferences(entity, sub)) {
                                // build Job
                                Job job = new Job();
                                job.setId(UUID.randomUUID().toString());
                                job.setType("notify");
                                job.setStatus("pending");
                                job.setAttempts(0);
                                job.setCreatedAt(now);
                                job.setUpdatedAt(now);

                                Map<String, Object> payload = new HashMap<>();
                                payload.put("petId", entity.getId());
                                payload.put("subscriberId", sub.getId());
                                payload.put("delta", "updated"); // simple delta indicator

                                job.setPayload(payload);
                                job.setPetIds(Collections.singletonList(entity.getId()));
                                job.setSubscriberIds(Collections.singletonList(sub.getId()));

                                // try to persist job (async) and log failures
                                try {
                                    CompletableFuture<java.util.UUID> addFuture = entityService.addItem(
                                        Job.ENTITY_NAME,
                                        String.valueOf(Job.ENTITY_VERSION),
                                        job
                                    );
                                    // Wait for completion to ensure job creation before finishing processor
                                    addFuture.get();
                                } catch (Exception ex) {
                                    logger.warn("Failed to create notify job for subscriber {} and pet {}: {}", sub.getId(), entity.getId(), ex.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to retrieve subscribers for notify job creation: {}", ex.getMessage());
                }
            }

        } catch (Exception ex) {
            logger.error("Error in PetUpsertProcessor: {}", ex.getMessage(), ex);
            // Do not throw - return entity as-is; Cyoda will persist the entity state
        }

        return entity;
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private static boolean listEquals(List<String> a, List<String> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return new HashSet<>(a).equals(new HashSet<>(b));
    }

    private static List<String> mergeLists(List<String> existing, List<String> incoming) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (existing != null) set.addAll(existing);
        if (incoming != null) set.addAll(incoming);
        return new ArrayList<>(set);
    }

    private static boolean matchesSubscriberPreferences(Pet pet, Subscriber sub) {
        if (sub.getPreferences() == null) return false;

        // Species matching: if subscriber has species list, pet's species must be in it
        List<String> prefSpecies = sub.getPreferences().getSpecies();
        if (prefSpecies != null && !prefSpecies.isEmpty()) {
            if (pet.getSpecies() == null || pet.getSpecies().isBlank()) return false;
            boolean speciesMatch = false;
            for (String s : prefSpecies) {
                if (s != null && s.equalsIgnoreCase(pet.getSpecies())) {
                    speciesMatch = true;
                    break;
                }
            }
            if (!speciesMatch) return false;
        }

        // Tags matching: if subscriber specified tags, require at least one tag match
        List<String> prefTags = sub.getPreferences().getTags();
        if (prefTags != null && !prefTags.isEmpty()) {
            List<String> petTags = pet.getTags();
            if (petTags == null || petTags.isEmpty()) return false;
            boolean tagMatch = false;
            for (String t : prefTags) {
                if (t == null) continue;
                for (String pt : petTags) {
                    if (pt != null && pt.equalsIgnoreCase(t)) {
                        tagMatch = true;
                        break;
                    }
                }
                if (tagMatch) break;
            }
            if (!tagMatch) return false;
        }

        return true;
    }
}