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

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@Component
public class PetEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public PetEnrichmentProcessor(SerializerFactory serializerFactory,
                                  EntityService entityService,
                                  ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
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
            // Normalize species: trim + lowercase to keep canonical form (e.g., "cat", "dog")
            if (entity.getSpecies() != null) {
                String normalizedSpecies = normalizeToLower(entity.getSpecies());
                entity.setSpecies(normalizedSpecies);
            }

            // Normalize breed: trim and title-case (e.g., "golden retriever")
            if (entity.getBreed() != null) {
                String normalizedBreed = normalizeToTitle(entity.getBreed());
                entity.setBreed(normalizedBreed);
            }

            // Normalize name: trim if present
            if (entity.getName() != null) {
                entity.setName(entity.getName().trim());
            }

            // Clean vaccinations list: remove blank/null entries and trim
            if (entity.getVaccinations() != null) {
                List<String> cleanedVax = new ArrayList<>();
                for (String v : entity.getVaccinations()) {
                    if (v != null) {
                        String t = v.trim();
                        if (!t.isBlank()) cleanedVax.add(t);
                    }
                }
                entity.setVaccinations(cleanedVax);
            }

            // Clean existing photoUrls list: remove blank/null entries and trim
            if (entity.getPhotoUrls() != null) {
                List<String> cleanedPhotos = new ArrayList<>();
                for (String p : entity.getPhotoUrls()) {
                    if (p != null) {
                        String t = p.trim();
                        if (!t.isBlank()) cleanedPhotos.add(t);
                    }
                }
                entity.setPhotoUrls(cleanedPhotos);
            }

            // If photoUrls are missing or empty, attempt to fetch from sourceUrl
            if ((entity.getPhotoUrls() == null || entity.getPhotoUrls().isEmpty())
                    && entity.getSourceUrl() != null && !entity.getSourceUrl().isBlank()) {
                try {
                    List<String> fetched = fetchPhotosFromSource(entity.getSourceUrl());
                    if (fetched != null && !fetched.isEmpty()) {
                        entity.setPhotoUrls(fetched);
                        logger.info("Fetched {} photos for pet id={}", fetched.size(), entity.getId());
                    } else {
                        logger.debug("No photos found at sourceUrl for pet id={}", entity.getId());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to fetch photos for pet id={}, url={}: {}", entity.getId(), entity.getSourceUrl(), e.getMessage());
                }
            }

        } catch (Exception ex) {
            logger.error("Error enriching Pet entity id={}: {}", entity.getId(), ex.getMessage(), ex);
        }

        return entity;
    }

    // Helpers

    private String normalizeToLower(String value) {
        if (value == null) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeToTitle(String value) {
        if (value == null) return null;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        String[] parts = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
            if (i < parts.length - 1) sb.append(" ");
        }
        return sb.toString();
    }

    private List<String> fetchPhotosFromSource(String sourceUrl) throws Exception {
        if (sourceUrl == null || sourceUrl.isBlank()) return List.of();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            logger.debug("Non-success response fetching sourceUrl {}: status={}", sourceUrl, response.statusCode());
            return List.of();
        }

        String body = response.body();
        if (body == null || body.isBlank()) return List.of();

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            logger.debug("Failed to parse JSON from sourceUrl {}: {}", sourceUrl, e.getMessage());
            return List.of();
        }

        List<String> results = new ArrayList<>();

        // Common candidate fields to look for
        String[] candidates = new String[] {"photoUrls", "photos", "images", "image", "pictures"};
        for (String field : candidates) {
            JsonNode node = root.path(field);
            if (!node.isMissingNode() && node.isArray()) {
                for (JsonNode n : node) {
                    if (n.isTextual()) {
                        String url = n.asText().trim();
                        if (!url.isBlank()) results.add(url);
                    }
                }
                if (!results.isEmpty()) return results;
            } else if (!node.isMissingNode() && node.isTextual()) {
                String url = node.asText().trim();
                if (!url.isBlank()) {
                    results.add(url);
                    return results;
                }
            }
        }

        // If not found, search recursively for image-like URLs (jpg/png/gif/jpeg)
        collectImageUrls(root, results);
        return results;
    }

    private void collectImageUrls(JsonNode node, List<String> results) {
        if (node == null || node.isMissingNode()) return;
        if (node.isTextual()) {
            String val = node.asText().trim();
            if (!val.isBlank() && isImageUrl(val)) {
                results.add(val);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectImageUrls(child, results);
            }
            return;
        }
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fname = fieldNames.next();
                collectImageUrls(node.get(fname), results);
            }
        }
    }

    private boolean isImageUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return (lower.startsWith("http://") || lower.startsWith("https://"))
                && (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif"));
    }
}