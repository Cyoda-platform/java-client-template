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
            // Enrichment from external Petstore API when source indicates Petstore
            String source = entity.getSource();
            String externalId = entity.getId();

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
                            var node = objectMapper.readTree(response.body());

                            // Photos: if current entity has no photos, try to populate from source
                            if ((entity.getPhotos() == null || entity.getPhotos().isEmpty()) && node.has("photos") && node.get("photos").isArray()) {
                                List<String> photos = new ArrayList<>();
                                node.get("photos").forEach(n -> {
                                    if (n != null && n.isTextual() && !n.asText().isBlank()) {
                                        photos.add(n.asText());
                                    }
                                });
                                if (!photos.isEmpty()) {
                                    entity.setPhotos(photos);
                                    logger.info("Enriched pet [{}] with {} photos from Petstore", externalId, photos.size());
                                }
                            }

                            // Map age if missing
                            if (entity.getAge() == null && node.has("age") && node.get("age").canConvertToInt()) {
                                int ageFromSource = node.get("age").asInt();
                                entity.setAge(ageFromSource);
                                logger.info("Enriched pet [{}] age from Petstore: {}", externalId, ageFromSource);
                            }

                            // Map description/breed/healthNotes if missing or blank
                            if ((entity.getDescription() == null || entity.getDescription().isBlank()) && node.has("description") && node.get("description").isTextual()) {
                                entity.setDescription(node.get("description").asText());
                            }
                            if ((entity.getBreed() == null || entity.getBreed().isBlank()) && node.has("breed") && node.get("breed").isTextual()) {
                                entity.setBreed(node.get("breed").asText());
                            }
                            if ((entity.getHealthNotes() == null || entity.getHealthNotes().isBlank()) && node.has("healthNotes") && node.get("healthNotes").isTextual()) {
                                entity.setHealthNotes(node.get("healthNotes").asText());
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
            Integer age = entity.getAge();
            if (age != null) {
                if (age > 24) {
                    int normalized = Math.max(0, age / 12);
                    if (normalized != age) {
                        logger.info("Normalizing age for pet [{}]: {} -> {} (months->years heuristic)", entity.getTechnicalId(), age, normalized);
                        entity.setAge(normalized);
                    }
                }
            }

            // Ensure status is set to a reasonable default if missing
            if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                // Default to AVAILABLE after enrichment if possible
                entity.setStatus("AVAILABLE");
                logger.info("Setting default status AVAILABLE for pet [{}]", entity.getTechnicalId());
            }

            // Additional lightweight enrichment: if photos exist but healthNotes missing, mark healthNotes as 'Unknown' placeholder
            if ((entity.getHealthNotes() == null || entity.getHealthNotes().isBlank()) && entity.getPhotos() != null && !entity.getPhotos().isEmpty()) {
                entity.setHealthNotes("Not specified");
            }

        } catch (Exception e) {
            // Do not fail the entire workflow on enrichment errors; log and continue with current entity state
            logger.error("Unexpected error during Pet enrichment for technicalId {}: {}", entity.getTechnicalId(), e.getMessage(), e);
        }

        return entity;
    }
}