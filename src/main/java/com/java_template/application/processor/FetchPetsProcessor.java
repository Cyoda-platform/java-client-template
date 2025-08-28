package com.java_template.application.processor;
import com.java_template.application.entity.petimportjob.version_1.PetImportJob;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.time.Duration;

@Component
public class FetchPetsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchPetsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public FetchPetsProcessor(SerializerFactory serializerFactory,
                              EntityService entityService,
                              ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetImportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetImportJob.class)
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

    private boolean isValidEntity(PetImportJob entity) {
        return entity != null && entity.isValid();
    }

    private PetImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetImportJob> context) {
        PetImportJob job = context.entity();

        // Initialize counters and default status
        int fetchedCount = 0;
        int createdCount = 0;
        job.setError(null);

        // Mark as FETCHING
        try {
            job.setStatus("FETCHING");
        } catch (Exception e) {
            logger.warn("Unable to set status to FETCHING on job {}", job.getJobId(), e);
        }

        String sourceUrl = job.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            String err = "sourceUrl is blank";
            logger.error("PetImportJob {} failed: {}", job.getJobId(), err);
            job.setStatus("FAILED");
            job.setError(err);
            job.setFetchedCount(fetchedCount);
            job.setCreatedCount(createdCount);
            return job;
        }

        List<Pet> petsToCreate = new ArrayList<>();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                String err = "Failed to fetch pets, statusCode=" + statusCode;
                logger.error("PetImportJob {}: {}", job.getJobId(), err);
                job.setStatus("FAILED");
                job.setError(err);
                job.setFetchedCount(fetchedCount);
                job.setCreatedCount(createdCount);
                return job;
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                logger.info("PetImportJob {}: fetched empty body", job.getJobId());
            } else {
                JsonNode root = objectMapper.readTree(body);
                ArrayNode arrayNode = null;
                if (root.isArray()) {
                    arrayNode = (ArrayNode) root;
                } else if (root.has("items") && root.get("items").isArray()) {
                    arrayNode = (ArrayNode) root.get("items");
                }

                if (arrayNode != null) {
                    fetchedCount = arrayNode.size();
                    for (JsonNode node : arrayNode) {
                        Pet p = mapJsonNodeToPet(node, job);
                        // Only include valid Pet candidates (must satisfy required minimal fields to be persisted)
                        if (p != null) {
                            petsToCreate.add(p);
                        }
                    }
                } else {
                    // Try single object mapping
                    Pet p = mapJsonNodeToPet(root, job);
                    if (p != null) {
                        petsToCreate.add(p);
                        fetchedCount = 1;
                    }
                }
            }

        } catch (IOException | InterruptedException e) {
            String err = "Exception during HTTP fetch: " + e.getMessage();
            logger.error("PetImportJob {}: {}", job.getJobId(), err, e);
            job.setStatus("FAILED");
            job.setError(err);
            job.setFetchedCount(fetchedCount);
            job.setCreatedCount(createdCount);
            return job;
        } catch (Exception e) {
            String err = "Unexpected error while fetching pets: " + e.getMessage();
            logger.error("PetImportJob {}: {}", job.getJobId(), err, e);
            job.setStatus("FAILED");
            job.setError(err);
            job.setFetchedCount(fetchedCount);
            job.setCreatedCount(createdCount);
            return job;
        }

        // If there are pets to create, persist them via entityService
        if (!petsToCreate.isEmpty()) {
            try {
                CompletableFuture<List<java.util.UUID>> idsFuture = entityService.addItems(
                        Pet.ENTITY_NAME,
                        Pet.ENTITY_VERSION,
                        petsToCreate
                );
                List<java.util.UUID> ids = idsFuture.get();
                createdCount = ids != null ? ids.size() : 0;
            } catch (InterruptedException | ExecutionException e) {
                String err = "Failed to persist fetched pets: " + e.getMessage();
                logger.error("PetImportJob {}: {}", job.getJobId(), err, e);
                job.setStatus("FAILED");
                job.setError(err);
                job.setFetchedCount(fetchedCount);
                job.setCreatedCount(createdCount);
                return job;
            }
        } else {
            logger.info("PetImportJob {}: no pets found to create", job.getJobId());
        }

        // Update job counters and status - move to CREATING or COMPLETED depending on createdCount
        job.setFetchedCount(fetchedCount);
        job.setCreatedCount(createdCount);

        if (createdCount > 0) {
            // In the workflow Fetching -> Creating, set to CREATING to let next processor run if present.
            // Many flows expect CreatePetsProcessor afterwards; but since we already created, mark COMPLETED.
            job.setStatus("COMPLETED");
            job.setError(null);
        } else {
            // No pets created, consider job completed but with zero results
            job.setStatus("COMPLETED");
            job.setError(null);
        }

        return job;
    }

    private Pet mapJsonNodeToPet(JsonNode node, PetImportJob job) {
        if (node == null || node.isNull()) return null;
        Pet pet = new Pet();

        // petId: try common fields, otherwise generate
        String petId = null;
        if (node.hasNonNull("petId")) petId = node.get("petId").asText(null);
        if (petId == null && node.hasNonNull("id")) petId = node.get("id").asText(null);
        if (petId == null && node.hasNonNull("uuid")) petId = node.get("uuid").asText(null);
        if (petId == null) petId = UUID.randomUUID().toString();
        pet.setPetId(petId);

        // name
        String name = null;
        if (node.hasNonNull("name")) name = node.get("name").asText(null);
        if (name == null && node.hasNonNull("title")) name = node.get("title").asText(null);
        if (name == null) name = "Unknown";
        pet.setName(name);

        // species
        String species = null;
        if (node.hasNonNull("species")) species = node.get("species").asText(null);
        if (species == null && node.hasNonNull("type")) species = node.get("type").asText(null);
        if (species == null) species = "unknown";
        pet.setSpecies(species);

        // breed
        if (node.hasNonNull("breed")) pet.setBreed(node.get("breed").asText(null));
        else if (node.hasNonNull("race")) pet.setBreed(node.get("race").asText(null));
        else pet.setBreed(null);

        // age
        Integer age = null;
        if (node.hasNonNull("age")) {
            try {
                age = node.get("age").isInt() ? node.get("age").asInt() : Integer.valueOf(node.get("age").asText());
            } catch (Exception e) {
                age = null;
            }
        }
        pet.setAge(age);

        // gender
        if (node.hasNonNull("gender")) pet.setGender(node.get("gender").asText(null));
        else pet.setGender(null);

        // importedFrom
        String importedFrom = job.getSourceUrl();
        pet.setImportedFrom(importedFrom);

        // description
        if (node.hasNonNull("description")) pet.setDescription(node.get("description").asText(null));
        else if (node.hasNonNull("info")) pet.setDescription(node.get("info").asText(null));
        else pet.setDescription(null);

        // photoUrls
        List<String> photoUrls = new ArrayList<>();
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            for (JsonNode p : node.get("photoUrls")) {
                if (p != null && !p.asText().isBlank()) photoUrls.add(p.asText());
            }
        } else if (node.has("photos") && node.get("photos").isArray()) {
            for (JsonNode p : node.get("photos")) {
                if (p != null && !p.asText().isBlank()) photoUrls.add(p.asText());
            }
        } else if (node.hasNonNull("photoUrl")) {
            String single = node.get("photoUrl").asText(null);
            if (single != null && !single.isBlank()) photoUrls.add(single);
        }
        // ensure non-null list per Pet.isValid()
        if (photoUrls.isEmpty()) {
            // Pet.isValid requires non-null collection (can be empty), but it then checks entries for blank.
            // Since empty is allowed, keep empty list.
        }
        pet.setPhotoUrls(photoUrls);

        // status - default available
        pet.setStatus("AVAILABLE");

        // tags - ensure non-null
        List<String> tags = new ArrayList<>();
        if (node.has("tags") && node.get("tags").isArray()) {
            for (JsonNode t : node.get("tags")) {
                if (t != null && !t.asText().isBlank()) tags.add(t.asText());
            }
        }
        // Add an imported tag and a friendly tag based on age
        tags.add("imported");
        if (pet.getAge() != null) {
            if (pet.getAge() <= 1) tags.add("puppy/kitten");
            else if (pet.getAge() <= 3) tags.add("young");
            else if (pet.getAge() <= 8) tags.add("adult");
            else tags.add("senior");
        }
        pet.setTags(tags);

        // Final validation attempt: ensure required fields meet minimal non-blank criteria for Pet.isValid()
        // Pet.isValid requires petId, name, species, status non-blank and non-null lists for photoUrls and tags (we satisfy these)
        // If name or species were left as "Unknown"/"unknown" we still satisfy non-blank requirement.

        return pet;
    }
}