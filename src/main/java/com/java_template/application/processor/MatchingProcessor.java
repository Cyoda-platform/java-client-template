package com.java_template.application.processor;

import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class MatchingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MatchingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MatchingProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AdoptionJob.class)
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

    private boolean isValidEntity(AdoptionJob entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionJob> context) {
        AdoptionJob job = context.entity();
        if (job == null) {
            logger.warn("AdoptionJob entity is null in execution context");
            return null;
        }

        // Default failure handling
        try {
            // Load owner
            Owner owner = null;
            try {
                CompletableFuture<DataPayload> ownerFuture = entityService.getItem(Owner.ENTITY_NAME, Owner.ENTITY_VERSION, UUID.fromString(job.getOwnerId()));
                DataPayload ownerPayload = ownerFuture.get();
                if (ownerPayload != null && ownerPayload.getData() != null) {
                    owner = objectMapper.treeToValue(ownerPayload.getData(), Owner.class);
                }
            } catch (Exception e) {
                logger.error("Failed to load owner with id {}: {}", job.getOwnerId(), e.getMessage(), e);
            }

            if (owner == null) {
                logger.warn("Owner not found for job {}, marking job as FAILED", job.getId());
                job.setStatus("FAILED");
                job.setResultCount(0);
                job.setResultsPreview(Collections.emptyList());
                return job;
            }

            // Load all pets
            List<Pet> pets = new ArrayList<>();
            try {
                CompletableFuture<List<DataPayload>> petsFuture = entityService.getItems(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, null, null, null);
                List<DataPayload> petPayloads = petsFuture.get();
                if (petPayloads != null) {
                    for (DataPayload payload : petPayloads) {
                        if (payload != null && payload.getData() != null) {
                            try {
                                Pet p = objectMapper.treeToValue(payload.getData(), Pet.class);
                                pets.add(p);
                            } catch (Exception e) {
                                logger.warn("Failed to convert pet payload to Pet: {}", e.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to load pets: {}", e.getMessage(), e);
            }

            // Filter available pets only
            List<Pet> candidates = pets.stream()
                    .filter(Objects::nonNull)
                    .filter(p -> {
                        String s = p.getStatus();
                        return s != null && "AVAILABLE".equalsIgnoreCase(s.trim());
                    })
                    .collect(Collectors.toList());

            // Parse owner preferences JSON if present
            JsonNode prefsNode = null;
            try {
                if (owner.getPreferences() != null && !owner.getPreferences().isBlank()) {
                    prefsNode = objectMapper.readTree(owner.getPreferences());
                }
            } catch (Exception e) {
                logger.warn("Failed to parse owner preferences JSON for owner {}: {}", owner.getId(), e.getMessage());
            }

            String prefSpecies = null;
            String prefBreed = null;
            Integer prefAgeMax = null;
            Integer prefAgeMin = null;
            if (prefsNode != null) {
                if (prefsNode.has("species") && !prefsNode.get("species").isNull()) {
                    prefSpecies = prefsNode.get("species").asText(null);
                }
                if (prefsNode.has("breed") && !prefsNode.get("breed").isNull()) {
                    prefBreed = prefsNode.get("breed").asText(null);
                }
                if (prefsNode.has("ageMax") && prefsNode.get("ageMax").canConvertToInt()) {
                    prefAgeMax = prefsNode.get("ageMax").asInt();
                }
                if (prefsNode.has("ageMin") && prefsNode.get("ageMin").canConvertToInt()) {
                    prefAgeMin = prefsNode.get("ageMin").asInt();
                }
            }

            // Score candidates
            class Scored {
                Pet pet;
                int score;
                Scored(Pet pet, int score) { this.pet = pet; this.score = score; }
            }

            List<Scored> scoredList = new ArrayList<>();
            for (Pet p : candidates) {
                int score = 0;
                // species match strong weight
                if (prefSpecies != null && p.getSpecies() != null &&
                        prefSpecies.equalsIgnoreCase(p.getSpecies())) {
                    score += 50;
                }
                // breed match medium weight
                if (prefBreed != null && p.getBreed() != null &&
                        prefBreed.equalsIgnoreCase(p.getBreed())) {
                    score += 20;
                }
                // age preferences
                Integer petAge = p.getAge();
                if (petAge != null) {
                    if (prefAgeMax != null && petAge <= prefAgeMax) {
                        score += 10;
                    }
                    if (prefAgeMin != null && petAge >= prefAgeMin) {
                        score += 5;
                    }
                    // smaller age difference gives small bonus if both min/max provided
                    if (prefAgeMin != null && prefAgeMax != null) {
                        int range = Math.max(1, Math.abs(prefAgeMax - prefAgeMin));
                        int mid = (prefAgeMin + prefAgeMax) / 2;
                        int diff = Math.abs(petAge - mid);
                        int bonus = Math.max(0, 5 - (diff * 5 / range)); // 5 down to 0
                        score += bonus;
                    }
                }

                // fallback basic match: if no preferences provided give baseline score
                if (prefSpecies == null && prefBreed == null && prefAgeMax == null && prefAgeMin == null) {
                    score += 1;
                }

                scoredList.add(new Scored(p, score));
            }

            // Sort and pick top N (10)
            List<Scored> top = scoredList.stream()
                    .sorted(Comparator.comparingInt((Scored s) -> s.score).reversed())
                    .limit(10)
                    .collect(Collectors.toList());

            List<String> previewIds = top.stream()
                    .map(s -> s.pet != null ? s.pet.getId() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            job.setResultsPreview(previewIds);
            job.setResultCount(previewIds.size());
            job.setStatus("COMPLETED");

            logger.info("AdoptionJob {} completed: {} matches found", job.getId(), job.getResultCount());
            return job;

        } catch (Exception ex) {
            logger.error("Unexpected error while processing AdoptionJob {}: {}", job.getId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            job.setResultCount(0);
            job.setResultsPreview(Collections.emptyList());
            return job;
        }
    }
}