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
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.io.IOException;

@Component
public class CreatePetsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreatePetsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    public CreatePetsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetImportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetImportJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract PetImportJob: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract PetImportJob: " + error.getMessage());
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
        if (job == null) return null;

        // Mark job as CREATING while we persist pets
        try {
            job.setStatus("CREATING");
        } catch (Exception ex) {
            logger.warn("Unable to set status to CREATING: {}", ex.getMessage());
        }

        String sourceUrl = job.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            logger.error("Source URL is missing for job {}", job.getJobId());
            job.setStatus("FAILED");
            job.setError("Missing sourceUrl");
            job.setFetchedCount(0);
            job.setCreatedCount(0);
            return job;
        }

        List<Pet> petsToCreate = new ArrayList<>();
        int fetched = 0;

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                String msg = "Failed to fetch pets from sourceUrl. HTTP status: " + statusCode;
                logger.error(msg);
                job.setStatus("FAILED");
                job.setError(msg);
                job.setFetchedCount(0);
                job.setCreatedCount(0);
                return job;
            }

            String body = response.body();
            JsonNode root = objectMapper.readTree(body);
            if (root == null || root.isNull()) {
                logger.warn("Empty response body when fetching pets for job {}", job.getJobId());
            } else if (root.isArray()) {
                for (JsonNode node : root) {
                    Pet pet = mapNodeToPet(node, job);
                    if (pet != null) {
                        petsToCreate.add(pet);
                    }
                }
            } else if (root.isObject()) {
                // If API returns an object with a data array or pets field, try common fields
                if (root.has("pets") && root.get("pets").isArray()) {
                    for (JsonNode node : root.get("pets")) {
                        Pet pet = mapNodeToPet(node, job);
                        if (pet != null) petsToCreate.add(pet);
                    }
                } else if (root.has("data") && root.get("data").isArray()) {
                    for (JsonNode node : root.get("data")) {
                        Pet pet = mapNodeToPet(node, job);
                        if (pet != null) petsToCreate.add(pet);
                    }
                } else {
                    // single pet object
                    Pet pet = mapNodeToPet(root, job);
                    if (pet != null) petsToCreate.add(pet);
                }
            }

            fetched = petsToCreate.size();
            job.setFetchedCount(fetched);

            if (petsToCreate.isEmpty()) {
                job.setCreatedCount(0);
                job.setStatus("COMPLETED");
                return job;
            }

            // Persist pets using EntityService (this will trigger Pet workflows)
            CompletableFuture<List<java.util.UUID>> idsFuture = entityService.addItems(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    petsToCreate
            );
            List<java.util.UUID> createdIds = idsFuture.get();
            job.setCreatedCount(createdIds != null ? createdIds.size() : 0);
            job.setStatus("COMPLETED");
            job.setError(null);
            logger.info("Job {} created {} pets (fetched {}).", job.getJobId(), job.getCreatedCount(), job.getFetchedCount());
        } catch (IOException | InterruptedException e) {
            logger.error("IO/Interrupted while creating pets for job {}: {}", job.getJobId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setError("Fetch error: " + e.getMessage());
            job.setCreatedCount(0);
            job.setFetchedCount(fetched);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.error("Execution error while persisting pets for job {}: {}", job.getJobId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setError("Persistence error: " + e.getMessage());
            job.setCreatedCount(0);
            job.setFetchedCount(fetched);
        } catch (Exception e) {
            logger.error("Unexpected error in CreatePetsProcessor for job {}: {}", job.getJobId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setError("Unexpected error: " + e.getMessage());
            job.setCreatedCount(0);
            job.setFetchedCount(fetched);
        }

        return job;
    }

    private Pet mapNodeToPet(JsonNode node, PetImportJob job) {
        if (node == null || node.isNull()) return null;
        try {
            Pet pet = new Pet();

            // petId: try common fields id or petId, otherwise generate
            String petId = null;
            if (node.hasNonNull("petId")) petId = node.get("petId").asText(null);
            if ((petId == null || petId.isBlank()) && node.hasNonNull("id")) petId = node.get("id").asText(null);
            if (petId == null || petId.isBlank()) petId = UUID.randomUUID().toString();
            pet.setPetId(petId);

            // name
            String name = node.hasNonNull("name") ? node.get("name").asText() : ("pet-" + petId);
            pet.setName(name);

            // species
            String species = node.hasNonNull("species") ? node.get("species").asText() : "unknown";
            pet.setSpecies(species);

            // breed
            String breed = node.hasNonNull("breed") ? node.get("breed").asText(null) : null;
            pet.setBreed(breed);

            // age
            Integer age = null;
            if (node.hasNonNull("age") && node.get("age").canConvertToInt()) {
                age = node.get("age").asInt();
            }
            pet.setAge(age);

            // gender
            String gender = node.hasNonNull("gender") ? node.get("gender").asText(null) : null;
            pet.setGender(gender);

            // importedFrom
            String importedFrom = job != null && job.getSourceUrl() != null ? job.getSourceUrl() : "external";
            pet.setImportedFrom(importedFrom);

            // description
            String description = node.hasNonNull("description") ? node.get("description").asText(null) : null;
            pet.setDescription(description);

            // photoUrls - ensure non-null list and non-blank entries
            List<String> photoUrls = new ArrayList<>();
            if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
                for (JsonNode urlNode : node.get("photoUrls")) {
                    if (urlNode != null && !urlNode.isNull()) {
                        String u = urlNode.asText(null);
                        if (u != null && !u.isBlank()) photoUrls.add(u);
                    }
                }
            } else if (node.hasNonNull("photoUrl")) {
                String single = node.get("photoUrl").asText(null);
                if (single != null && !single.isBlank()) photoUrls.add(single);
            }
            // Ensure at least empty list
            pet.setPhotoUrls(photoUrls);

            // status - set available by default for created pets
            pet.setStatus("AVAILABLE");

            // tags - ensure non-null list; add imported tag
            List<String> tags = new ArrayList<>();
            if (node.has("tags") && node.get("tags").isArray()) {
                for (JsonNode tnode : node.get("tags")) {
                    if (tnode != null && !tnode.isNull()) {
                        String t = tnode.asText(null);
                        if (t != null && !t.isBlank()) tags.add(t);
                    }
                }
            }
            // add a marker tag to indicate import source
            String sourceTag = job != null && job.getJobId() != null ? ("imported:" + job.getJobId()) : "imported";
            tags.add(sourceTag);
            pet.setTags(tags);

            // Validate minimal Pet structure to avoid failing isValid later when persisted by Cyoda
            if (pet.getPhotoUrls() == null) pet.setPhotoUrls(new ArrayList<>());
            if (pet.getTags() == null) pet.setTags(new ArrayList<>());
            if (pet.getName() == null || pet.getName().isBlank()) pet.setName("Unnamed Pet");
            if (pet.getSpecies() == null || pet.getSpecies().isBlank()) pet.setSpecies("unknown");
            if (pet.getStatus() == null || pet.getStatus().isBlank()) pet.setStatus("AVAILABLE");
            if (pet.getPetId() == null || pet.getPetId().isBlank()) pet.setPetId(UUID.randomUUID().toString());

            return pet;
        } catch (Exception e) {
            logger.warn("Failed to map node to Pet: {}", e.getMessage(), e);
            return null;
        }
    }
}