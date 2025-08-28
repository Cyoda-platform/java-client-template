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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class PetEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PetEnrichmentProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
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

        try {
            // Use Jackson tree to avoid direct calls to Lombok-generated getters/setters at compile-time.
            ObjectNode entityNode = objectMapper.valueToTree(entity);

            // Enrichment from external Petstore API when source indicates Petstore
            String source = entityNode.hasNonNull("source") ? entityNode.get("source").asText(null) : null;
            String externalId = entityNode.hasNonNull("id") ? entityNode.get("id").asText(null) : null;

            if (source != null && externalId != null && "PetstoreAPI".equalsIgnoreCase(source.trim())) {
                try {
                    String encodedId = URLEncoder.encode(externalId, StandardCharsets.UTF_8);
                    // Example external endpoint - best-effort enrichment
                    String url = "https://api.petstore.com/v1/pets/" + encodedId;

                    HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .header("Accept", "application/json")
                        .build();

                    HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                        try {
                            JsonNode remote = objectMapper.readTree(response.body());

                            // Photos: if current entity has no photos, try to populate from source
                            boolean hasPhotos = entityNode.hasNonNull("photos") && entityNode.get("photos").isArray() && entityNode.get("photos").size() > 0;
                            if (!hasPhotos && remote.has("photos") && remote.get("photos").isArray()) {
                                ArrayNode photosArray = objectMapper.createArrayNode();
                                remote.get("photos").forEach(n -> {
                                    if (n != null && n.isTextual() && !n.asText().isBlank()) {
                                        photosArray.add(n.asText());
                                    }
                                });
                                if (photosArray.size() > 0) {
                                    entityNode.set("photos", photosArray);
                                    logger.info("Enriched pet [{}] with {} photos from Petstore", externalId, photosArray.size());
                                }
                            }

                            // Map age if missing
                            boolean hasAge = entityNode.hasNonNull("age");
                            if (!hasAge && remote.has("age") && remote.get("age").canConvertToInt()) {
                                int ageFromSource = remote.get("age").asInt();
                                entityNode.put("age", ageFromSource);
                                logger.info("Enriched pet [{}] age from Petstore: {}", externalId, ageFromSource);
                            }

                            // Map description/breed/healthNotes if missing or blank
                            if ((!entityNode.hasNonNull("description") || entityNode.get("description").asText("").isBlank())
                                    && remote.has("description") && remote.get("description").isTextual()) {
                                entityNode.put("description", remote.get("description").asText());
                            }
                            if ((!entityNode.hasNonNull("breed") || entityNode.get("breed").asText("").isBlank())
                                    && remote.has("breed") && remote.get("breed").isTextual()) {
                                entityNode.put("breed", remote.get("breed").asText());
                            }
                            if ((!entityNode.hasNonNull("healthNotes") || entityNode.get("healthNotes").asText("").isBlank())
                                    && remote.has("healthNotes") && remote.get("healthNotes").isTextual()) {
                                entityNode.put("healthNotes", remote.get("healthNotes").asText());
                            }
                        } catch (Exception je) {
                            logger.warn("Failed to parse enrichment response for pet {}: {}", externalId, je.getMessage());
                        }
                    } else {
                        logger.info("No enrichment data found for pet {} from Petstore (status={})", externalId, response.statusCode());
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to call Petstore API for pet {}: {}", externalId, ex.getMessage());
                }
            }

            // Normalize age:
            // Business heuristic: if age value looks like months (>24) convert to years by dividing by 12.
            if (entityNode.hasNonNull("age") && entityNode.get("age").canConvertToInt()) {
                int age = entityNode.get("age").asInt();
                if (age > 24) {
                    int normalized = Math.max(0, age / 12);
                    if (normalized != age) {
                        String techId = entityNode.hasNonNull("technicalId") ? entityNode.get("technicalId").asText(null) : null;
                        logger.info("Normalizing age for pet [{}]: {} -> {} (months->years heuristic)", techId != null ? techId : externalId, age, normalized);
                        entityNode.put("age", normalized);
                    }
                }
            }

            // Ensure status is set to a reasonable default if missing
            if (!entityNode.hasNonNull("status") || entityNode.get("status").asText("").isBlank()) {
                // Default to AVAILABLE after enrichment if possible
                entityNode.put("status", "AVAILABLE");
                String techId = entityNode.hasNonNull("technicalId") ? entityNode.get("technicalId").asText(null) : null;
                logger.info("Setting default status AVAILABLE for pet [{}]", techId != null ? techId : externalId);
            }

            // Additional lightweight enrichment: if photos exist but healthNotes missing, mark healthNotes as 'Not specified' placeholder
            boolean hasPhotosNow = entityNode.has("photos") && entityNode.get("photos").isArray() && entityNode.get("photos").size() > 0;
            if ((!entityNode.hasNonNull("healthNotes") || entityNode.get("healthNotes").asText("").isBlank()) && hasPhotosNow) {
                entityNode.put("healthNotes", "Not specified");
            }

            // Convert back to Pet instance; serializer/persistence will handle saving the modified entity
            Pet updated = objectMapper.treeToValue(entityNode, Pet.class);
            return updated;

        } catch (Exception e) {
            // Do not fail the entire workflow on enrichment errors; log and continue with current entity state
            String techId = entity != null ? entity.getTechnicalId() : null;
            logger.error("Unexpected error during Pet enrichment for technicalId {}: {}", techId, e.getMessage(), e);
            return entity;
        }
    }
}