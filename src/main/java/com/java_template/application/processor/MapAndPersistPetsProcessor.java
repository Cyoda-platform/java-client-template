package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class MapAndPersistPetsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MapAndPersistPetsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    public MapAndPersistPetsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestionJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestionJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();
        if (job == null) {
            logger.warn("IngestionJob is null in context");
            return job;
        }

        // Initialize or reset summary counters
        IngestionJob.Summary summary = job.getSummary();
        if (summary == null) {
            summary = new IngestionJob.Summary();
            summary.setCreated(0);
            summary.setUpdated(0);
            summary.setFailed(0);
            job.setSummary(summary);
        } else {
            if (summary.getCreated() == null) summary.setCreated(0);
            if (summary.getUpdated() == null) summary.setUpdated(0);
            if (summary.getFailed() == null) summary.setFailed(0);
        }

        String sourceUrl = job.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            logger.error("Source URL is missing for ingestion job requestedBy={}", job.getRequestedBy());
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
            summary.setFailed(summary.getFailed() + 1);
            return job;
        }

        List<Pet> petsToPersist = new ArrayList<>();
        int localFailed = 0;

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode != 200) {
                logger.error("Failed to fetch data from {}. HTTP status: {}", sourceUrl, statusCode);
                job.setStatus("FAILED");
                job.setCompletedAt(Instant.now().toString());
                summary.setFailed(summary.getFailed() + 1);
                return job;
            }

            String body = response.body();
            JsonNode root = objectMapper.readTree(body);

            // Support either an array at root or an object with a "pets" array
            JsonNode arrayNode = root.isArray() ? root : root.path("pets");
            if (arrayNode == null || !arrayNode.isArray()) {
                logger.error("Fetched payload does not contain a pets array: {}", body);
                job.setStatus("FAILED");
                job.setCompletedAt(Instant.now().toString());
                summary.setFailed(summary.getFailed() + 1);
                return job;
            }

            for (JsonNode node : arrayNode) {
                try {
                    Pet pet = new Pet();
                    // Ensure unique technical id for the persisted entity
                    pet.setId(UUID.randomUUID().toString());

                    String extId = node.path("id").asText(null);
                    if (extId != null && !extId.isBlank()) {
                        // preserve external id information in metadata.tags to avoid inventing fields
                        Pet.Metadata meta = new Pet.Metadata();
                        meta.setImages(new ArrayList<>());
                        meta.setTags(new ArrayList<>());
                        meta.setEnrichedAt(Instant.now().toString());
                        meta.getTags().add("externalId:" + extId);
                        pet.setMetadata(meta);
                    } else {
                        Pet.Metadata meta = new Pet.Metadata();
                        meta.setImages(new ArrayList<>());
                        meta.setTags(new ArrayList<>());
                        meta.setEnrichedAt(Instant.now().toString());
                        pet.setMetadata(meta);
                    }

                    // Map common fields with safe defaults
                    String name = node.path("name").asText(null);
                    if (name != null) pet.setName(name);

                    String species = node.path("species").asText(null);
                    if (species != null) pet.setSpecies(species);

                    String breed = node.path("breed").asText(null);
                    if (breed != null) pet.setBreed(breed);

                    // ageMonths may be present as number or as "ageMonths" or "age"
                    Integer ageMonths = null;
                    if (node.has("ageMonths") && node.get("ageMonths").canConvertToInt()) {
                        ageMonths = node.get("ageMonths").asInt();
                    } else if (node.has("age") && node.get("age").canConvertToInt()) {
                        ageMonths = node.get("age").asInt();
                    }
                    if (ageMonths != null) pet.setAgeMonths(ageMonths);

                    // status default to AVAILABLE if not provided
                    String status = node.path("status").asText(null);
                    if (status == null || status.isBlank()) {
                        pet.setStatus("AVAILABLE");
                    } else {
                        pet.setStatus(status);
                    }

                    // Ensure metadata exists
                    if (pet.getMetadata() == null) {
                        Pet.Metadata meta = new Pet.Metadata();
                        meta.setImages(new ArrayList<>());
                        meta.setTags(new ArrayList<>());
                        meta.setEnrichedAt(Instant.now().toString());
                        pet.setMetadata(meta);
                    } else {
                        if (pet.getMetadata().getImages() == null) pet.getMetadata().setImages(new ArrayList<>());
                        if (pet.getMetadata().getTags() == null) pet.getMetadata().setTags(new ArrayList<>());
                        if (pet.getMetadata().getEnrichedAt() == null) pet.getMetadata().setEnrichedAt(Instant.now().toString());
                    }

                    // Validate pet using its own isValid() method
                    if (pet.isValid()) {
                        petsToPersist.add(pet);
                    } else {
                        logger.warn("Skipping invalid pet mapping from external data: {}", node.toString());
                        localFailed++;
                    }
                } catch (Exception ex) {
                    logger.error("Failed to map a pet from external node: {}", node.toString(), ex);
                    localFailed++;
                }
            }

            // Persist created pets (if any)
            if (!petsToPersist.isEmpty()) {
                try {
                    CompletableFuture<List<java.util.UUID>> idsFuture =
                        entityService.addItems(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, petsToPersist);
                    List<java.util.UUID> createdIds = idsFuture.get();
                    int createdCount = createdIds != null ? createdIds.size() : 0;
                    summary.setCreated(summary.getCreated() + createdCount);
                    logger.info("Persisted {} pets for ingestion job requestedBy={}", createdCount, job.getRequestedBy());
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to persist pets for ingestion job: {}", job.getRequestedBy(), e);
                    // Consider all attempted as failed in this case
                    localFailed += petsToPersist.size();
                }
            }

        } catch (Exception e) {
            logger.error("Unexpected error while mapping/persisting pets for ingestion job: {}", job.getRequestedBy(), e);
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
            summary.setFailed(summary.getFailed() + 1);
            return job;
        }

        // Update summary failed count
        summary.setFailed(summary.getFailed() + localFailed);

        // Determine final status
        if (summary.getFailed() != null && summary.getFailed() > 0) {
            job.setStatus("FAILED");
        } else {
            job.setStatus("COMPLETED");
        }

        job.setCompletedAt(Instant.now().toString());
        job.setSummary(summary);

        return job;
    }
}