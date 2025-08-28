package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class FetchPetstoreDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchPetstoreDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FetchPetstoreDataProcessor(SerializerFactory serializerFactory,
                                      EntityService entityService,
                                      ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
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

        // Set job as running and ensure startedAt exists
        try {
            job.setStatus("RUNNING");
            if (job.getStartedAt() == null || job.getStartedAt().isBlank()) {
                job.setStartedAt(Instant.now().toString());
            }

            String sourceUrl = job.getSourceUrl();
            if (sourceUrl == null || sourceUrl.isBlank()) {
                logger.error("Ingestion job missing sourceUrl");
                IngestionJob.Summary summary = new IngestionJob.Summary();
                summary.setCreated(0);
                summary.setUpdated(0);
                summary.setFailed(1);
                job.setSummary(summary);
                job.setCompletedAt(Instant.now().toString());
                job.setStatus("FAILED");
                return job;
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .GET()
                .header("Accept", "application/json")
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode != 200) {
                logger.error("Failed to fetch from sourceUrl: {} status={}", sourceUrl, statusCode);
                IngestionJob.Summary summary = new IngestionJob.Summary();
                summary.setCreated(0);
                summary.setUpdated(0);
                summary.setFailed(1);
                job.setSummary(summary);
                job.setCompletedAt(Instant.now().toString());
                job.setStatus("FAILED");
                return job;
            }

            String body = response.body();
            JsonNode root = objectMapper.readTree(body);

            List<Pet> petsToPersist = new ArrayList<>();
            int parseFailures = 0;

            if (root.isArray()) {
                for (JsonNode node : root) {
                    try {
                        Pet pet = objectMapper.treeToValue(node, Pet.class);
                        // Ensure minimal required pet fields exist; apply sensible defaults if missing
                        if (pet.getId() == null || pet.getId().isBlank()) {
                            pet.setId(UUID.randomUUID().toString());
                        }
                        if (pet.getName() == null || pet.getName().isBlank()) {
                            // skip invalid pet - name required
                            parseFailures++;
                            continue;
                        }
                        if (pet.getSpecies() == null || pet.getSpecies().isBlank()) {
                            pet.setSpecies("unknown");
                        }
                        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
                            pet.setStatus("AVAILABLE");
                        }
                        // Ensure metadata presence with non-null lists to satisfy Pet.isValid() if metadata is set
                        if (pet.getMetadata() != null) {
                            if (pet.getMetadata().getImages() == null) {
                                pet.getMetadata().setImages(new ArrayList<>());
                            }
                            if (pet.getMetadata().getTags() == null) {
                                pet.getMetadata().setTags(new ArrayList<>());
                            }
                        }
                        petsToPersist.add(pet);
                    } catch (Exception ex) {
                        logger.warn("Failed to parse pet record: {}", ex.getMessage());
                        parseFailures++;
                    }
                }
            } else if (root.isObject()) {
                // If single object returned, try to map it as one pet or as wrapper containing array "pets"
                if (root.has("pets") && root.get("pets").isArray()) {
                    for (JsonNode node : root.get("pets")) {
                        try {
                            Pet pet = objectMapper.treeToValue(node, Pet.class);
                            if (pet.getId() == null || pet.getId().isBlank()) {
                                pet.setId(UUID.randomUUID().toString());
                            }
                            if (pet.getName() == null || pet.getName().isBlank()) {
                                parseFailures++;
                                continue;
                            }
                            if (pet.getSpecies() == null || pet.getSpecies().isBlank()) {
                                pet.setSpecies("unknown");
                            }
                            if (pet.getStatus() == null || pet.getStatus().isBlank()) {
                                pet.setStatus("AVAILABLE");
                            }
                            if (pet.getMetadata() != null) {
                                if (pet.getMetadata().getImages() == null) {
                                    pet.getMetadata().setImages(new ArrayList<>());
                                }
                                if (pet.getMetadata().getTags() == null) {
                                    pet.getMetadata().setTags(new ArrayList<>());
                                }
                            }
                            petsToPersist.add(pet);
                        } catch (Exception ex) {
                            logger.warn("Failed to parse pet record in wrapper: {}", ex.getMessage());
                            parseFailures++;
                        }
                    }
                } else {
                    // Try mapping single pet object
                    try {
                        Pet pet = objectMapper.treeToValue(root, Pet.class);
                        if (pet.getId() == null || pet.getId().isBlank()) {
                            pet.setId(UUID.randomUUID().toString());
                        }
                        if (pet.getName() == null || pet.getName().isBlank()) {
                            parseFailures++;
                        } else {
                            if (pet.getSpecies() == null || pet.getSpecies().isBlank()) {
                                pet.setSpecies("unknown");
                            }
                            if (pet.getStatus() == null || pet.getStatus().isBlank()) {
                                pet.setStatus("AVAILABLE");
                            }
                            if (pet.getMetadata() != null) {
                                if (pet.getMetadata().getImages() == null) {
                                    pet.getMetadata().setImages(new ArrayList<>());
                                }
                                if (pet.getMetadata().getTags() == null) {
                                    pet.getMetadata().setTags(new ArrayList<>());
                                }
                            }
                            petsToPersist.add(pet);
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to parse single pet object: {}", ex.getMessage());
                        parseFailures++;
                    }
                }
            } else {
                logger.error("Unexpected JSON payload structure from sourceUrl");
                IngestionJob.Summary summary = new IngestionJob.Summary();
                summary.setCreated(0);
                summary.setUpdated(0);
                summary.setFailed(1);
                job.setSummary(summary);
                job.setCompletedAt(Instant.now().toString());
                job.setStatus("FAILED");
                return job;
            }

            int created = 0;
            int failed = parseFailures;
            int updated = 0;

            if (!petsToPersist.isEmpty()) {
                try {
                    CompletableFuture<List<java.util.UUID>> idsFuture = entityService.addItems(
                        Pet.ENTITY_NAME,
                        Pet.ENTITY_VERSION,
                        petsToPersist
                    );
                    List<java.util.UUID> persistedIds = idsFuture.get();
                    if (persistedIds != null) {
                        created = persistedIds.size();
                    }
                } catch (Exception ex) {
                    logger.error("Failed to persist pets: {}", ex.getMessage(), ex);
                    // mark all as failed in case of persistence error
                    failed += petsToPersist.size();
                }
            }

            IngestionJob.Summary summary = new IngestionJob.Summary();
            summary.setCreated(created);
            summary.setUpdated(updated);
            summary.setFailed(failed);
            job.setSummary(summary);

            job.setCompletedAt(Instant.now().toString());
            job.setStatus(failed == 0 ? "COMPLETED" : "FAILED");

            return job;
        } catch (Exception ex) {
            logger.error("Error during FetchPetstoreDataProcessor execution: {}", ex.getMessage(), ex);
            IngestionJob.Summary summary = new IngestionJob.Summary();
            summary.setCreated(0);
            summary.setUpdated(0);
            summary.setFailed(1);
            job.setSummary(summary);
            job.setCompletedAt(Instant.now().toString());
            job.setStatus("FAILED");
            return job;
        }
    }
}