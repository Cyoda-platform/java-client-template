package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
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
public class TransformPetDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransformPetDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public TransformPetDataProcessor(SerializerFactory serializerFactory,
                                     EntityService entityService,
                                     ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetIngestionJob.class)
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

    private boolean isValidEntity(PetIngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private PetIngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetIngestionJob> context) {
        PetIngestionJob job = context.entity();

        // Ensure errors list is initialized
        if (job.getErrors() == null) {
            job.setErrors(new ArrayList<>());
        }

        try {
            // Mark job in transforming state
            job.setStatus("TRANSFORMING");

            String sourceUrl = job.getSourceUrl();
            if (sourceUrl == null || sourceUrl.isBlank()) {
                job.getErrors().add("Missing sourceUrl on ingestion job");
                job.setStatus("FAILED");
                return job;
            }

            // Fetch data from source URL
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .GET()
                .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                job.getErrors().add("Failed to fetch source, HTTP status: " + statusCode);
                job.setStatus("FAILED");
                return job;
            }

            String body = response.body();
            JsonNode root = objectMapper.readTree(body);

            List<Pet> petsToPersist = new ArrayList<>();

            // Helper to map a node -> Pet with safe defaults
            java.util.function.Consumer<JsonNode> mapNode = node -> {
                try {
                    // Try to convert node directly to Pet. Unknown/missing fields are ignored by ObjectMapper.
                    Pet pet = objectMapper.treeToValue(node, Pet.class);

                    // Ensure required fields for Pet.isValid()
                    if (pet.getId() == null || pet.getId().isBlank()) {
                        pet.setId(UUID.randomUUID().toString());
                    }
                    if (pet.getImportedAt() == null || pet.getImportedAt().isBlank()) {
                        pet.setImportedAt(Instant.now().toString());
                    }
                    if (pet.getSource() == null || pet.getSource().isBlank()) {
                        // Prefer jobName, fallback to sourceUrl
                        pet.setSource(job.getJobName() != null && !job.getJobName().isBlank()
                            ? job.getJobName() : job.getSourceUrl());
                    }
                    if (pet.getStatus() == null || pet.getStatus().isBlank()) {
                        // Default to AVAILABLE for newly ingested pets
                        pet.setStatus("AVAILABLE");
                    }
                    // Sanitize photos/tags: ensure lists have no blank entries (Pet.isValid will check later)
                    if (pet.getPhotos() != null) {
                        List<String> photosClean = new ArrayList<>();
                        for (String p : pet.getPhotos()) {
                            if (p != null && !p.isBlank()) photosClean.add(p);
                        }
                        pet.setPhotos(photosClean);
                    }
                    if (pet.getTags() != null) {
                        List<String> tagsClean = new ArrayList<>();
                        for (String t : pet.getTags()) {
                            if (t != null && !t.isBlank()) tagsClean.add(t);
                        }
                        pet.setTags(tagsClean);
                    }

                    // Only add pet if it satisfies minimal validity (id, name, species, status). We will still allow adding
                    // and let downstream persistence/criteria validate further, but avoid obvious empty entries.
                    if (pet.getName() == null || pet.getName().isBlank()
                        || pet.getSpecies() == null || pet.getSpecies().isBlank()) {
                        job.getErrors().add("Skipping pet without required name/species for id: " + pet.getId());
                    } else {
                        petsToPersist.add(pet);
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to map a pet item from source: {}", ex.getMessage(), ex);
                    job.getErrors().add("Failed to map item: " + ex.getMessage());
                }
            };

            if (root.isArray()) {
                for (JsonNode item : root) {
                    mapNode.accept(item);
                }
            } else if (root.isObject()) {
                // Try common wrapper keys where APIs may place arrays
                JsonNode items = null;
                if (root.has("pets") && root.get("pets").isArray()) {
                    items = root.get("pets");
                } else if (root.has("items") && root.get("items").isArray()) {
                    items = root.get("items");
                } else if (root.has("data") && root.get("data").isArray()) {
                    items = root.get("data");
                }

                if (items != null) {
                    for (JsonNode item : items) {
                        mapNode.accept(item);
                    }
                } else {
                    // Treat single object as single pet
                    mapNode.accept(root);
                }
            } else {
                job.getErrors().add("Unexpected payload format from source");
                job.setStatus("FAILED");
                return job;
            }

            if (petsToPersist.isEmpty()) {
                if (job.getErrors().isEmpty()) {
                    job.getErrors().add("No valid pet items extracted from source");
                }
                job.setProcessedCount(0);
                job.setStatus("FAILED");
                return job;
            }

            // Persist transformed Pet entities (allowed: add other entities)
            CompletableFuture<List<java.util.UUID>> idsFuture = entityService.addItems(
                Pet.ENTITY_NAME,
                Pet.ENTITY_VERSION,
                petsToPersist
            );
            List<java.util.UUID> persistedIds = idsFuture.get();

            job.setProcessedCount(persistedIds != null ? persistedIds.size() : petsToPersist.size());
            // Advance job to next phase (PERSISTING)
            job.setStatus("PERSISTING");

        } catch (Exception e) {
            logger.error("Error while transforming pet data for job {}: {}", job.getJobName(), e.getMessage(), e);
            job.getErrors().add(e.getMessage() != null ? e.getMessage() : e.toString());
            job.setStatus("FAILED");
        }

        return job;
    }
}